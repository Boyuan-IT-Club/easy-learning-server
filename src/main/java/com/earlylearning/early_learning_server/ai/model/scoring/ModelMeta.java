package com.earlylearning.early_learning_server.ai.model.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 生成该结果的模型与提示词版本，用于追溯。与评分标准版本、内容版本分别管理。
 */
public record ModelMeta(

        @JsonProperty("model") String model,
        @JsonProperty("prompt_version") String promptVersion) {
}
