package com.earlylearning.early_learning_server.storage.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 批量签发下载地址的请求体。
 *
 * <p>契约：{@code minItems: 1}、{@code maxItems: 100}、{@code uniqueItems: true}、每项匹配 {@code ^CF_[A-Za-z0-9_-]+$}。
 *
 * <p><b>这里刻意不加 Bean Validation 注解</b>：那类失败由通用处理器回报，字段名是 Java 字段名
 * （{@code fileCodes}），而 {@code details.field_path} 是请求体里的 JSON Pointer（{@code /file_codes}）。
 * 所以数量、去重、命名空间三项校验都放在服务层，以便如实回报失败位置。
 */
public record SignDownloadUrlsRequest(

        @JsonProperty("file_codes") List<String> fileCodes) {
}
