package com.earlylearning.early_learning_server.storage.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.earlylearning.early_learning_server.storage.CloudFile;
import com.earlylearning.early_learning_server.storage.CloudFileKind;
import com.earlylearning.early_learning_server.storage.CloudFileStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 `CloudFile`：11 个必填字段，**不含 `object_key`**、内部路径或存储凭据。
 *
 * <p>{@code @JsonInclude(ALWAYS)} 是承重的：契约要求 `duration_ms` 对非音频**显式输出 null**，
 * 而一旦全局限定 {@code NON_NULL}，这个键会被整个省略、客户端解析失败。M0 对这个回归有专门的变异测试。
 *
 * <p>时间字段输出 UTC ISO-8601。数据库连接按 `serverTimezone` 读出的 {@link LocalDateTime} 是那个时区的
 * 墙上时间，因此这里按 JVM 时区解释后再转 UTC——**要求 JVM 时区与 JDBC 的 serverTimezone 一致**
 * （部署时由容器 TZ 保证）。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CloudFileResponse(

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
        @JsonProperty("updated_at") String updatedAt) {

    public static CloudFileResponse from(CloudFile file) {
        return new CloudFileResponse(
                file.getId(),
                file.getFileCode(),
                file.getFileName(),
                file.getFileKind(),
                file.getMimeType(),
                file.getSizeBytes(),
                file.getDurationMs(),
                file.getStatus(),
                file.getSha256(),
                isoUtc(file.getCreatedAt()),
                isoUtc(file.getUpdatedAt()));
    }

    private static String isoUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);
    }
}
