package com.earlylearning.early_learning_server.material.model.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 故事叙述活动：可选的标准故事音频与图片分组。content_item_id 唯一且不固定组数；
 * rubric_item_code 显式映射本故事的图片分组到统一评分规则的条目，不按图片序号猜测。
 */
public record NarrationActivity(

        @JsonProperty("activity_id") String activityId,

        @JsonProperty("type") String type,

        Config config) implements Activity {

    public static final String TYPE = "STORY_NARRATION";

    public record Config(

            @JsonProperty("audio_file_code") @JsonInclude(JsonInclude.Include.NON_NULL) String audioFileCode,

            @JsonProperty("content_items") List<ContentItem> contentItems) {

        public record ContentItem(

                @JsonProperty("content_item_id") String contentItemId,

                @JsonProperty("image_file_codes") List<String> imageFileCodes,

                @JsonProperty("rubric_item_code") String rubricItemCode) {
        }
    }
}
