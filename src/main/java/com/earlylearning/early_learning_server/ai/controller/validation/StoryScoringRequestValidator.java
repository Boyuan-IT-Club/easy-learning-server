package com.earlylearning.early_learning_server.ai.controller.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.ai.dto.ContentItem;
import com.earlylearning.early_learning_server.ai.dto.ImageContext;
import com.earlylearning.early_learning_server.ai.dto.StoryScoringRequest;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringLimits;
import com.earlylearning.early_learning_server.ai.service.scoring.AiStoryScoringService;
import com.earlylearning.early_learning_server.ai.service.scoring.ScoringImageService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import static com.earlylearning.early_learning_server.ai.controller.validation.RequestFieldChecks.invalid;
import static com.earlylearning.early_learning_server.ai.controller.validation.RequestFieldChecks.requireNotNull;
import static com.earlylearning.early_learning_server.ai.controller.validation.RequestFieldChecks.requireText;
import static com.earlylearning.early_learning_server.ai.controller.validation.RequestFieldChecks.requireTrue;

/**
 * 故事评分请求的语义校验。
 *
 * <p>「完整校验维度、分组、版本后」才受理，其中最容易漏的是图片与分组的对应关系：
 * images 必须恰好覆盖分组引用的全部编号，形态可以是内联图片、服务端按编号取图
 * （SERVER_FETCH）或教师确认过的图片说明（CONFIRMED_DESCRIPTION）。
 */
@Component
public class StoryScoringRequestValidator {

    private final ImageContextValidator imageContextValidator;
    private final ScoringLimits scoringLimits;

    public StoryScoringRequestValidator(ImageContextValidator imageContextValidator, ScoringLimits scoringLimits) {
        this.imageContextValidator = imageContextValidator;
        this.scoringLimits = scoringLimits;
    }

    public void validate(StoryScoringRequest request) {
        requireNotNull(request, "request");
        requireText(request.requestId(), "request_id");
        requireText(request.inputRevision(), "input_revision");
        requireNotNull(request.businessType(), "business_type");
        requireText(request.activityId(), "activity_id");

        // 契约里 text_confirmed 是 enum [true]：没确认就不该提交
        requireTrue(request.textConfirmed(), "text_confirmed");
        // 空字符串是合法的：它表示"已确认无回应"，与缺失不同
        requireNotNull(request.confirmedText(), "confirmed_text");
        requireText(request.storyContext(), "story_context");

        // 契约把 text_length / image_count 列为部署上限，要真的执行；按「一次定位首个失败」只报第一个
        requireWithinLimit(request.confirmedText(), scoringLimits.maxTextLength());
        requireWithinLimit(request.storyContext(), scoringLimits.maxTextLength());

        Set<String> referencedImages = validateContentItems(request.contentItems());
        validateImages(request.images(), referencedImages);
    }

    /** @return 所有分组引用到的图片编号 */
    private Set<String> validateContentItems(List<ContentItem> contentItems) {
        if (contentItems == null || contentItems.isEmpty()) {
            throw invalid("content_items");
        }
        Set<String> groupIds = new HashSet<>();
        Set<String> referencedImages = new HashSet<>();
        for (int i = 0; i < contentItems.size(); i++) {
            ContentItem item = contentItems.get(i);
            String path = "content_items/" + i;
            requireNotNull(item, path);
            requireText(item.contentItemId(), path + "/content_item_id");
            requireText(item.rubricItemCode(), path + "/rubric_item_code");
            if (!groupIds.add(item.contentItemId())) {
                throw invalid(path + "/content_item_id");
            }
            if (item.imageFileCodes() == null || item.imageFileCodes().isEmpty()) {
                throw invalid(path + "/image_file_codes");
            }
            Set<String> imagesOfItem = new HashSet<>();
            for (String fileCode : item.imageFileCodes()) {
                requireText(fileCode, path + "/image_file_codes");
                // 同一编号在多个分组出现是合法的（细则 v2：图7 进两个分组）；
                // 同一分组里重复列同一张图没有意义，仍然拒收。
                if (!imagesOfItem.add(fileCode)) {
                    throw invalid(path + "/image_file_codes");
                }
                referencedImages.add(fileCode);
            }
        }
        return referencedImages;
    }

    /** images 必须恰好覆盖分组引用的全部图片，且每条都要有真实内容或确认说明。 */
    /** 超过部署上限 → 413 + {@code details.limit}（契约的 PayloadTooLarge 形状）。 */
    private void requireWithinLimit(String text, int maximum) {
        if (text != null && text.length() > maximum) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.TEXT_LENGTH, maximum));
        }
    }

    private void validateImages(List<ImageContext> images, Set<String> referencedImages) {
        if (images == null || images.isEmpty()) {
            throw invalid("images");
        }
        Set<String> provided = new HashSet<>();
        for (int i = 0; i < images.size(); i++) {
            ImageContext image = images.get(i);
            String path = "images/" + i;
            // 三种形态都收：契约 /api/ai/score 的 images 明确写「由服务端按 file_code 取图（SERVER_FETCH），
            // 或教师确认过的图片说明（CONFIRMED_DESCRIPTION）。不再要求客户端内联图片内容」。
            // 取回动作在提交的同步路径上完成（见 AiStoryScoringService.toInput → ScoringImageService），
            // 编号不可读会当场 404/409/410/413，而不是落成任务失败。
            imageContextValidator.validate(image, path);
            if (!provided.add(image.fileCode())) {
                throw invalid(path + "/file_code");
            }
        }
        if (provided.size() > scoringLimits.maxImageCount()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.IMAGE_COUNT, scoringLimits.maxImageCount()));
        }
        if (!provided.equals(referencedImages)) {
            // 多给、少给、或只发了编号没给内容，都在这里被挡下
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "images 与 content_items 引用的图片不一致：分组引用 " + referencedImages
                            + "，实际提供 " + provided,
                    ApiErrorDetails.atField("/images"));
        }
    }
}
