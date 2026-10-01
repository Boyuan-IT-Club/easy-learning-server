package com.earlylearning.early_learning_server.ai.model.scoring.story;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 故事评分任务的结果：契约里成功任务的 {@code result} 就是这个形状。 */
public record StoryScoringResult(@JsonProperty("ai_score") AiScore aiScore) {
}
