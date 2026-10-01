package com.earlylearning.early_learning_server.material.model.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 图片排序活动：儿童按正确顺序排列图片项。items 顺序即展示顺序，
 * correct_order 与 items 一一对应（发布校验保证集合相等且不重复）。
 */
public record SortingActivity(

        @JsonProperty("activity_id") String activityId,

        @JsonProperty("type") String type,

        Config config) implements Activity {

    public static final String TYPE = "IMAGE_SORTING";

    public record Config(

            List<Item> items,

            @JsonProperty("correct_order") List<String> correctOrder) {

        public record Item(

                @JsonProperty("item_id") String itemId,

                @JsonProperty("file_code") String fileCode) {
        }
    }
}
