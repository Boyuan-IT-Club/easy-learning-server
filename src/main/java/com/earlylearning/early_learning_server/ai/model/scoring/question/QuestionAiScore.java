package com.earlylearning.early_learning_server.ai.model.scoring.question;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.Evidence;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单题一次作答的评分。
 *
 * <p>模型元数据不在这个对象里：契约把它放在 HTTP 任务结果的外层（{@link AnswerScoringResult}），
 * 并明确"保存时不往此对象添加字段"。
 *
 * @param rubricVersion 实际采用的评分标准版本
 * @param score         只能为整数 0/1/2；null 不能作为成功结果
 * @param maxScore      固定 2：「单项满分固定 2」
 * @param reason        针对本次回答的完整评分理由，不能为空
 * @param evidence      可为空数组，但每一条都必须是确认文本里的真实片段，不能编造引文
 */
public record QuestionAiScore(

        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("score") Integer score,
        @JsonProperty("max_score") Integer maxScore,
        @JsonProperty("reason") String reason,
        @JsonProperty("evidence") List<Evidence> evidence) {

    public static final int MAX_SCORE = 2;
}
