package com.earlylearning.early_learning_server.audit.interfaces.dto;

import java.util.List;

import com.earlylearning.early_learning_server.audit.domain.AuditPage;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 审计分页的管理端形状，字段与官方文件列表一致。 */
public record AuditPageResponse(
        @JsonProperty("items") List<AuditLogResponse> items,
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total") long total) {

    public static AuditPageResponse from(AuditPage page) {
        return new AuditPageResponse(page.items().stream().map(AuditLogResponse::from).toList(),
                page.page(), page.size(), page.total());
    }
}
