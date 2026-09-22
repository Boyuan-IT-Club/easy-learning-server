package com.earlylearning.early_learning_server.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aliyun.oss")
public record OssProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String bucketName,
        @NotBlank String accessKeyId,
        @NotBlank String accessKeySecret,
        @DefaultValue("900") @Min(1) @Max(604800) long downloadUrlTtlSeconds) {

    /** 避免 record 默认输出包含凭证。 */
    @Override
    public String toString() {
        return "OssProperties[credentials=REDACTED]";
    }
}
