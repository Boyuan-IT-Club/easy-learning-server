package com.earlylearning.early_learning_server.ai.score;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评分的微观结构。
 *
 * <p>**与宏观结构形状不同**：宏观是「维度 + 覆盖请求全部图片分组的条目」，
 * 微观是「维度 + 叙事产生性」。所以两者不共用 {@link AiScoreSection}——
 * 早先共用过一次，结果是微观被要求返回它根本不该有的 {@code content_items}，
 * 而真正必填的 {@code productivity} 被漏掉。
 */
public record MicrostructureSection(

        @JsonProperty("dimensions") List<ScoreDimension> dimensions,
        @JsonProperty("productivity") ProductivityStat productivity) {
}
