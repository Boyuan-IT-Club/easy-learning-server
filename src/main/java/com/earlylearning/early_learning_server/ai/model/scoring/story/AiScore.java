package com.earlylearning.early_learning_server.ai.model.scoring.story;

import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 故事评分结果（契约的 AIScore v2）。
 *
 * @param schemaVersion 冻结的结构版本，固定为 2
 * @param summary       整体文字概述，可为 null；不是总分
 * @param microstructure 微观结构：维度 + 叙事产生性（与宏观形状不同，见 {@link MicrostructureSection}）
 */
public record AiScore(

        @JsonProperty("schema_version") Integer schemaVersion,
        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("summary") String summary,
        @JsonProperty("macrostructure") AiScoreSection macrostructure,
        @JsonProperty("microstructure") MicrostructureSection microstructure,
        @JsonProperty("model_meta") ModelMeta modelMeta) {

    public static final int SCHEMA_VERSION = 2;
}
