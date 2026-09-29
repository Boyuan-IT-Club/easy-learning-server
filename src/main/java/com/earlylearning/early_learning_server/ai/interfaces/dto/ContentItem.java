package com.earlylearning.early_learning_server.ai.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 故事里的一个图片分组。
 *
 * @param contentItemId   叙事活动内唯一的分组编号
 * @param imageFileCodes  该分组的实际图片编号，顺序即展示顺序
 * @param rubricItemCode  统一评分规则中的图片评分条目编号；所有故事共用，靠它把本故事的图片分组映射到统一规则
 */
public record ContentItem(

        @JsonProperty("content_item_id") String contentItemId,
        @JsonProperty("image_file_codes") List<String> imageFileCodes,
        @JsonProperty("rubric_item_code") String rubricItemCode) {
}
