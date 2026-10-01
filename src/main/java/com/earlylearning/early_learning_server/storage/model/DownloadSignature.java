package com.earlylearning.early_learning_server.storage.model;

import java.time.Instant;

/** 一个文件签出的只读下载地址（领域结果；URL 字符串化是 web 层的事）。 */
public record DownloadSignature(String fileCode,
                                String url,
                                Instant expiresAt,
                                Long sizeBytes,
                                String sha256,
                                String mimeType) {
}
