package com.earlylearning.early_learning_server.storage.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 `AdminFilePage`：当前页记录 + 分页元数据。
 */
public record AdminFilePageResponse(

        @JsonProperty("items") List<AdminFileResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total") long total) {
}
