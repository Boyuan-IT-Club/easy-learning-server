package com.earlylearning.early_learning_server.storage.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 `DownloadSignature`：一条已签发的只读下载地址及其校验信息。
 *
 * <p>{@code download_url} 是预签名地址，**不得写入日志、也不得持久化**（契约原文）。
 * 客户端下载后应按 {@code size_bytes} 与 {@code sha256} 自行核对。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DownloadSignatureResponse(

        @JsonProperty("file_code") String fileCode,
        @JsonProperty("download_url") String downloadUrl,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("mime_type") String mimeType) {
}
