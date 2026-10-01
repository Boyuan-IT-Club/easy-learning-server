package com.earlylearning.early_learning_server.ai.interfaces.validation;
import com.earlylearning.early_learning_server.ai.interfaces.dto.ScoringQuestion;
import com.earlylearning.early_learning_server.ai.interfaces.dto.AnswerScoringRequest;

import com.earlylearning.early_learning_server.ai.domain.scoring.ScoringLimits;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import static com.earlylearning.early_learning_server.ai.interfaces.validation.RequestFieldChecks.invalid;
import static com.earlylearning.early_learning_server.ai.interfaces.validation.RequestFieldChecks.requireNotNull;
import static com.earlylearning.early_learning_server.ai.interfaces.validation.RequestFieldChecks.requireText;
import static com.earlylearning.early_learning_server.ai.interfaces.validation.RequestFieldChecks.requireTrue;

/**
 * 单题评分请求的语义校验。
 *
 * <p>与故事评分的差别在图片：单题评分允许为空（「纯文本故事依据足够时可空」），
 * 因此这里只逐张校验形态完整，不做覆盖性检查——没有分组清单可比对。
 *
 * <p>{@code hint} 允许空字符串（这道题不设提示），但不允许缺失：契约把它列为必填字段。
 * 本类不校验"提示后作答必须带非空提示"——预设提示由课程配置决定，且契约允许它为空。
 */
@Component
public class AnswerScoringRequestValidator {

    private final ImageContextValidator imageContextValidator;
    private final ScoringLimits limits;

    public AnswerScoringRequestValidator(ImageContextValidator imageContextValidator, ScoringLimits limits) {
        this.imageContextValidator = imageContextValidator;
        this.limits = limits;
    }

    public void validate(AnswerScoringRequest request) {
        requireNotNull(request, "request");
        requireText(request.requestId(), "request_id");
        requireText(request.inputRevision(), "input_revision");
        requireNotNull(request.businessType(), "business_type");
        requireText(request.activityId(), "activity_id");

        // 契约里 text_confirmed 是 enum [true]：没确认就不该提交
        requireTrue(request.textConfirmed(), "text_confirmed");
        // 空字符串合法：它表示"已确认无回应"，与缺失不同
        requireNotNull(request.confirmedText(), "confirmed_text");
        requireText(request.storyContext(), "story_context");

        requireWithinLimit(request.confirmedText(), limits.maxTextLength());
        requireWithinLimit(request.storyContext(), limits.maxTextLength());

        validateQuestion(request.question());

        if (request.attempt() == null) {
            throw invalid("attempt");
        }

        // 契约的 required 含 images：空数组合法，但字段不能整缺
        requireNotNull(request.images(), "images");
        if (request.images().size() > limits.maxImageCount()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.IMAGE_COUNT, limits.maxImageCount()));
        }
        for (int i = 0; i < request.images().size(); i++) {
            imageContextValidator.validate(request.images().get(i), "images/" + i);
        }
    }

    /** 超过部署上限 → 413 + {@code details.limit}（契约的 PayloadTooLarge 形状）。 */
    private void requireWithinLimit(String text, int maximum) {
        if (text != null && text.length() > maximum) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.TEXT_LENGTH, maximum));
        }
    }


    private void validateQuestion(ScoringQuestion question) {
        requireNotNull(question, "question");
        requireText(question.questionId(), "question/question_id");
        requireText(question.text(), "question/text");
        requireNotNull(question.hint(), "question/hint");
    }
}
