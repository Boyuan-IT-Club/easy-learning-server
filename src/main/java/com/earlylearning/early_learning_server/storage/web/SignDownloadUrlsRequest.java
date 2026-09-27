package com.earlylearning.early_learning_server.storage.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 批量签发下载地址的请求体。
 *
 * <p>契约：`minItems: 1`、`maxItems: 100`、`uniqueItems: true`、每项匹配 `^CF_[A-Za-z0-9_-]+$`。
 *
 * <p><b>这里刻意不加 Bean Validation 注解</b>：那类失败由通用处理器回报，字段名是 Java 字段名
 * （`fileCodes`），而契约要求 `details.field_path` 是**请求体里的 JSON Pointer**（`/file_codes`）。
 * 所以数量、去重、命名空间三项校验都放在服务层，以便如实回报失败位置。
 */
public record SignDownloadUrlsRequest(

        @JsonProperty("file_codes") List<String> fileCodes) {
}
