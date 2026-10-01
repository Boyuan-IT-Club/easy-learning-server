package com.earlylearning.early_learning_server.ai.model.scoring.question;
import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;

import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单题评分任务的结果：契约里成功任务的 {@code result} 就是这个形状。
 *
 * <p>题号与 {@code attempt} 由服务端按请求写入——客户端靠 {@code attempt} 分辨这份分数属于
 * 提示前还是提示后的作答，进而算「提示前均分」与「最终均分」两个指标。
 *
 * @param modelMeta 模型元数据在这一层，不在 {@link QuestionAiScore} 里
 */
public record AnswerScoringResult(

        @JsonProperty("question_id") String questionId,
        @JsonProperty("attempt") Attempt attempt,
        @JsonProperty("ai_score") QuestionAiScore aiScore,
        @JsonProperty("model_meta") ModelMeta modelMeta) {
}
