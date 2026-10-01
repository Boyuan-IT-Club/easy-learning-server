package com.earlylearning.early_learning_server.storage.model;

import java.time.LocalDateTime;

import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.entity.CloudFileKind;
import com.earlylearning.early_learning_server.entity.CloudFileStatus;

/**
 * 官方文件在列表里的一行（读模型）：实体字段 + 它被内容引用的次数。
 */
public record FileSummary(Integer id,
                          String fileCode,
                          String fileName,
                          CloudFileKind fileKind,
                          String mimeType,
                          Long sizeBytes,
                          Integer durationMs,
                          CloudFileStatus status,
                          String sha256,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt,
                          int referenceCount) {

    public static FileSummary from(CloudFile file, int referenceCount) {
        return new FileSummary(file.getId(), file.getFileCode(), file.getFileName(), file.getFileKind(),
                file.getMimeType(), file.getSizeBytes(), file.getDurationMs(), file.getStatus(),
                file.getSha256(), file.getCreatedAt(), file.getUpdatedAt(), referenceCount);
    }
}
