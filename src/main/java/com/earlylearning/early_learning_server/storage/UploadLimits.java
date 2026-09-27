package com.earlylearning.early_learning_server.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 单文件上传上限。413 响应的 details.limit.maximum 取自这里。
 *
 * <p>必须低于 spring.servlet.multipart 的上限，否则超限会先被 Spring 拦下、带不出 details。
 */
@Validated
@ConfigurationProperties(prefix = "storage.upload")
public record UploadLimits(
        @DefaultValue("524288000") @Min(1) @Max(1073741824) long maxSizeBytes) {
}
