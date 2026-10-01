package com.earlylearning.early_learning_server.storage.client;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * OSS 接入配置：凭证、下载地址有效期、客户端超时与连接池。
 *
 * <p>超时与连接池不用 SDK 默认值——那些默认值（连接 50 秒、取连接无限等待、池 1024）
 * 在故障时会把请求挂住而不是快速失败。
 */
@Validated
@ConfigurationProperties(prefix = "aliyun.oss")
public record OssProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String bucketName,
        @NotBlank String accessKeyId,
        @NotBlank String accessKeySecret,
        @DefaultValue("900") @Min(1) @Max(604800) long downloadUrlTtlSeconds,
        @DefaultValue("5000") @Min(1000) @Max(60000) int connectionTimeoutMs,
        @DefaultValue("30000") @Min(1000) @Max(300000) int socketTimeoutMs,
        @DefaultValue("5000") @Min(100) @Max(60000) int connectionRequestTimeoutMs,
        @DefaultValue("64") @Min(1) @Max(1024) int maxConnections,
        @DefaultValue("2") @Min(0) @Max(5) int maxErrorRetry) {

    /** 避免 record 默认输出包含凭证。 */
    @Override
    public String toString() {
        return "OssProperties[credentials=REDACTED]";
    }
}
