package com.earlylearning.early_learning_server.ai.web;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.ai.score.ScoringLimits;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import static com.earlylearning.early_learning_server.ai.web.RequestFieldChecks.invalid;
import static com.earlylearning.early_learning_server.ai.web.RequestFieldChecks.requireNotNull;
import static com.earlylearning.early_learning_server.ai.web.RequestFieldChecks.requireText;
import static com.earlylearning.early_learning_server.ai.web.RequestFieldChecks.requireTrue;

/**
 * 故事评分请求的语义校验。
 *
 * <p>契约要求「完整校验维度、分组、版本后」才受理，其中最容易漏的是**图片与分组的对应关系**：
 * 「必须为所有分组引用的图片逐一提供真实图片或确认说明；**不得仅发送 file_code**」。
 */
@Component
public class StoryScoringRequestValidator {

    private final ImageContextValidator imageContextValidator;
    private final ScoringLimits limits;

    public StoryScoringRequestValidator(ImageContextValidator imageContextValidator, ScoringLimits limits) {
        this.imageContextValidator = imageContextValidator;
        this.limits = limits;
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
        requireWithinLimit(request.confirmedText(), limits.maxTextLength());
        requireWithinLimit(request.storyContext(), limits.maxTextLength());

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
            for (String fileCode : item.imageFileCodes()) {
                requireText(fileCode, path + "/image_file_codes");
                if (!referencedImages.add(fileCode)) {
                    throw invalid(path + "/image_file_codes");
                }
            }
        }
        return referencedImages;
    }

    /** images 必须**恰好覆盖**分组引用的全部图片，且每条都要有真实内容或确认说明。 */
    /** 超过部署上限 → 413 + `details.limit`（契约的 PayloadTooLarge 形状）。 */
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
            imageContextValidator.validate(image, path);
            if (!provided.add(image.fileCode())) {
                throw invalid(path + "/file_code");
            }
        }
        if (provided.size() > limits.maxImageCount()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.IMAGE_COUNT, limits.maxImageCount()));
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
