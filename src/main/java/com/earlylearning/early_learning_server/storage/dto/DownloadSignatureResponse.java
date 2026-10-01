package com.earlylearning.early_learning_server.storage.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.earlylearning.early_learning_server.storage.model.DownloadSignature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 {@code DownloadSignature}：一条已签发的只读下载地址及其校验信息。
 *
 * <p>{@code download_url} 是预签名地址，不得写入日志、也不得持久化。
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

    public static DownloadSignatureResponse from(DownloadSignature signature) {
        return new DownloadSignatureResponse(
                signature.fileCode(),
                signature.url(),
                isoUtc(signature.expiresAt()),
                signature.sizeBytes(),
                signature.sha256(),
                signature.mimeType());
    }

    private static String isoUtc(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);
    }
}
