package com.earlylearning.early_learning_server.storage.dto;

import java.util.List;

import com.earlylearning.early_learning_server.storage.model.FilePage;

/**
 * 官方文件分页列表的管理端形状。
 */
public record AdminFilePageResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("items") List<AdminFileResponse> items,
        @com.fasterxml.jackson.annotation.JsonProperty("page") int page,
        @com.fasterxml.jackson.annotation.JsonProperty("page_size") int pageSize,
        @com.fasterxml.jackson.annotation.JsonProperty("total") long total) {

    public static AdminFilePageResponse from(FilePage page) {
        return new AdminFilePageResponse(
                page.items().stream().map(AdminFileResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
