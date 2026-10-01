package com.earlylearning.early_learning_server.material.interfaces.dto;

import java.util.List;

import com.earlylearning.early_learning_server.material.domain.MaterialPage;
import com.earlylearning.early_learning_server.material.domain.MaterialSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理端版本列表页的契约形状。
 */
public record AssessmentMaterialPageResponse(

        @JsonProperty("items") List<AssessmentMaterialSummaryResponse> items,

        @JsonProperty("page") int page,

        @JsonProperty("page_size") int pageSize,

        @JsonProperty("total") long total) {

    public static AssessmentMaterialPageResponse from(MaterialPage page) {
        return new AssessmentMaterialPageResponse(
                page.items().stream().map(AssessmentMaterialSummaryResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }

    record AssessmentMaterialSummaryResponse(

            @JsonProperty("id") Integer id,

            @JsonProperty("official_material_code") String officialMaterialCode,

            @JsonProperty("content_version") String contentVersion,

            @JsonProperty("name") String name,

            @JsonProperty("status") String status,

            @JsonProperty("created_at") String createdAt,

            @JsonProperty("updated_at") String updatedAt) {

        static AssessmentMaterialSummaryResponse from(MaterialSummary summary) {
            return new AssessmentMaterialSummaryResponse(
                    summary.id(),
                    summary.officialMaterialCode(),
                    summary.contentVersion(),
                    summary.name(),
                    summary.status().value(),
                    AssessmentMaterialResponse.isoUtc(summary.createdAt()),
                    AssessmentMaterialResponse.isoUtc(summary.updatedAt()));
        }
    }
}
