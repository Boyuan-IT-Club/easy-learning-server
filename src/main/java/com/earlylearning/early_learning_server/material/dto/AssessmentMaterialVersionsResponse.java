package com.earlylearning.early_learning_server.material.dto;

import java.util.List;

import com.earlylearning.early_learning_server.material.model.MaterialVersionSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 平板端版本目录的契约形状：全量返回，无分页。
 */
public record AssessmentMaterialVersionsResponse(

        @JsonProperty("items") List<MaterialVersionResponse> items) {

    public record MaterialVersionResponse(

            @JsonProperty("official_material_code") String officialMaterialCode,

            @JsonProperty("content_version") String contentVersion,

            @JsonProperty("name") String name,

            @JsonProperty("status") String status,

            @JsonProperty("updated_at") String updatedAt) {

        public static MaterialVersionResponse from(MaterialVersionSummary summary) {
            return new MaterialVersionResponse(
                    summary.officialMaterialCode(),
                    summary.contentVersion(),
                    summary.name(),
                    summary.status().value(),
                    AssessmentMaterialResponse.isoUtc(summary.updatedAt()));
        }
    }

    public static AssessmentMaterialVersionsResponse from(List<MaterialVersionSummary> summaries) {
        return new AssessmentMaterialVersionsResponse(
                summaries.stream().map(MaterialVersionResponse::from).toList());
    }
}
