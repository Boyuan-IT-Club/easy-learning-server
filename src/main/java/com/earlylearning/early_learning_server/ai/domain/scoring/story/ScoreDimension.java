package com.earlylearning.early_learning_server.ai.domain.scoring.story;
import com.earlylearning.early_learning_server.ai.domain.scoring.Evidence;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一个评分维度。宏观与微观维度字段相同，只有 {@code item_code} 的取值范围不同。
 *
 * @param score    成功评分只能是 0/1/2；null 不能作为成功结果
 * @param maxScore 单项满分固定 2
 * @param reason   完整评分理由
 * @param evidence 可为空数组，但不能编造引文
 */
public record ScoreDimension(

        @JsonProperty("score") Integer score,
        @JsonProperty("max_score") Integer maxScore,
        @JsonProperty("reason") String reason,
        @JsonProperty("evidence") List<Evidence> evidence,
        @JsonProperty("item_code") String itemCode,
        @JsonProperty("item_name") String itemName) {
}
