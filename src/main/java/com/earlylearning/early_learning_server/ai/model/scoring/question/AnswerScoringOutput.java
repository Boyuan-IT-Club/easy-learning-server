package com.earlylearning.early_learning_server.ai.model.scoring.question;

import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;

/**
 * 单题评分适配器的输出：分数与模型元数据一起返回。
 *
 * <p>两者必须一起给：契约把 {@code model_meta} 放在任务结果的外层，不在 {@link QuestionAiScore} 里，
 * 所以适配器不能只返回分数。
 */
public record AnswerScoringOutput(QuestionAiScore score, ModelMeta modelMeta) {
}
