package com.earlylearning.early_learning_server.storage.dto;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;

import com.earlylearning.early_learning_server.storage.entity.CloudFileKind;
import com.earlylearning.early_learning_server.storage.entity.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.model.FileSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 {@code AdminFile}：{@code CloudFile} 的 11 个字段 + {@code reference_count}。
 *
 * <p>与 {@link CloudFileResponse} 并列而不是继承：契约里是两个独立 schema，
 * 显式写全字段比依赖序列化注解的继承/展开行为更可验证（M0 对 inclusion 行为有专门的变异测试）。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminFileResponse(

        @JsonProperty("id") Integer id,
        @JsonProperty("file_code") String fileCode,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_kind") CloudFileKind fileKind,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("duration_ms") Integer durationMs,
        @JsonProperty("status") CloudFileStatus status,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("reference_count") int referenceCount) {

    public static AdminFileResponse from(FileSummary summary) {
        return new AdminFileResponse(
                summary.id(), summary.fileCode(), summary.fileName(), summary.fileKind(),
                summary.mimeType(), summary.sizeBytes(), summary.durationMs(), summary.status(),
                summary.sha256(), isoUtc(summary.createdAt()), isoUtc(summary.updatedAt()),
                summary.referenceCount());
    }

    private static String isoUtc(java.time.LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(java.time.ZoneId.systemDefault())
                        .withZoneSameInstant(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
    }
}
