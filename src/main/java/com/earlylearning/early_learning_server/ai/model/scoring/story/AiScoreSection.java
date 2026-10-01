package com.earlylearning.early_learning_server.ai.model.scoring.story;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评分的宏观或微观结构：一组固定维度 + 覆盖请求全部图片分组的条目。
 *
 * <p>两部分形状相同，因此共用这个类型；维度的取值范围由 {@link ScoreValidator} 按所在部分校验。
 */
public record AiScoreSection(

        @JsonProperty("dimensions") List<ScoreDimension> dimensions,
        @JsonProperty("content_items") List<ScoreContentItem> contentItems) {
}
