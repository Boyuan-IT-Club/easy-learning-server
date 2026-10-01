package com.earlylearning.early_learning_server.material.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * 契约的 AssessmentMaterial：八个必填字段。activity_configs_json 原样输出冻结结构，
 * 里面的文件引用已是 CF_ 编号。
 */
public record AssessmentMaterialResponse(

        @JsonProperty("id") Integer id,

        @JsonProperty("official_material_code") String officialMaterialCode,

        @JsonProperty("content_version") String contentVersion,

        @JsonProperty("name") String name,

        @JsonProperty("activity_configs_json") JsonNode activityConfigsJson,

        @JsonProperty("status") String status,

        @JsonProperty("created_at") String createdAt,

        @JsonProperty("updated_at") String updatedAt) {

    public static AssessmentMaterialResponse from(AssessmentMaterial material, JsonNode config) {
        return new AssessmentMaterialResponse(
                material.getId(),
                material.getOfficialMaterialCode(),
                material.getContentVersion(),
                material.getName(),
                config,
                material.getStatus().value(),
                isoUtc(material.getCreatedAt()),
                isoUtc(material.getUpdatedAt()));
    }

    static String isoUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);
    }
}
