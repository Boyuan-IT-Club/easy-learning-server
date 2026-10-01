package com.earlylearning.early_learning_server.ai.model.scoring.question;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.task.Attempt;

/**
 * 单题评分适配器的输入：领域对象，不是 HTTP 请求模型。
 *
 * <p>图片已经解析成字节或确认说明（见 {@link ScoringImage}），适配器可以直接交给模型，
 * 不需要知道 {@code file_code} 怎么变成内容、也不需要碰存储。
 *
 * @param questionId    题号；结果要原样回带，供客户端对上题目
 * @param questionText  经业务确认的问题原文
 * @param hint          题目预设提示，可为空
 * @param attempt       提示前 / 提示后
 * @param confirmedText 经教师确认的回答原话；空串表示确认无回应
 * @param storyContext  经确认的故事依据
 * @param images        解析好的图片，可为空
 */
public record AnswerScoringInput(String questionId,
                                 String questionText,
                                 String hint,
                                 Attempt attempt,
                                 String confirmedText,
                                 String storyContext,
                                 List<ScoringImage> images) {

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
}
