package com.earlylearning.early_learning_server.material.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * ZIP 发布的部署上限：压缩包大小、解压总大小与包内条目数。
 * 业务上限必须低于 spring.servlet.multipart 的对应值，超限才由本模块返回带 details.limit 的 413。
 */
@Validated
@ConfigurationProperties(prefix = "material.publish")
public record MaterialPublishLimits(

        @DefaultValue("524288000") @Min(1) @Max(1073741824) long maxZipBytes,

        @DefaultValue("1073741824") @Min(1) long maxUncompressedBytes,

        @DefaultValue("200") @Min(1) @Max(1000) int maxFileCount) {
}
