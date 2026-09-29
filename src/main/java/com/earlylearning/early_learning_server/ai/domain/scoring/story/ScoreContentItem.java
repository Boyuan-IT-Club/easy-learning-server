package com.earlylearning.early_learning_server.ai.domain.scoring.story;
import com.earlylearning.early_learning_server.ai.domain.scoring.Evidence;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 图片分组维度的评分。
 *
 * @param contentItemId   对应请求里的一个图片分组
 * @param rubricItemCode  统一规则中的图片评分条目编号
 */
public record ScoreContentItem(

        @JsonProperty("score") Integer score,
        @JsonProperty("max_score") Integer maxScore,
        @JsonProperty("reason") String reason,
        @JsonProperty("evidence") List<Evidence> evidence,
        @JsonProperty("content_item_id") String contentItemId,
        @JsonProperty("rubric_item_code") String rubricItemCode) {
}
