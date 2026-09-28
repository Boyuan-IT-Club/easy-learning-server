package com.earlylearning.early_learning_server.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 错误响应中的 details：按错误类型提供必要的非敏感定位。
 *
 * <p>契约约束：
 * <ul>
 *   <li>{@code additionalProperties: false} —— 只能使用这张强类型表，不能用 Map；</li>
 *   <li>{@code minProperties: 1} —— 一旦给出 details，至少要有一个字段有值，不能是空对象。</li>
 * </ul>
 *
 * <p>不回显儿童原话、凭证、输入值或整份请求；一次定位首个失败即可。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetails(

        @JsonProperty("field_path") String fieldPath,
        @JsonProperty("file_code") String fileCode,
        @JsonProperty("rubric_item_code") String rubricItemCode,
        @JsonProperty("current_version") Integer currentVersion,
        @JsonProperty("limit") Limit limit,
        @JsonProperty("license_ids") List<Long> licenseIds,
        @JsonProperty("file_name") String fileName
) {

    public record Limit(@JsonProperty("name") LimitName name,
                        @JsonProperty("maximum") long maximum) {
    }

    /** 契约中 {@code limit.name} 的取值。 */
    public enum LimitName {
        @JsonProperty("size_bytes") SIZE_BYTES,
        @JsonProperty("duration_ms") DURATION_MS,
        @JsonProperty("image_count") IMAGE_COUNT,
        @JsonProperty("text_length") TEXT_LENGTH
    }

    /** 入参是契约要求的 JSON Pointer，形如 {@code "/file_name"}（必须以 / 开头）。 */
    public static ApiErrorDetails atField(String fieldPath) {
        return new ApiErrorDetails(fieldPath, null, null, null, null, null, null);
    }

    /** 定位到某个文件。 */
    public static ApiErrorDetails atFile(String fileCode) {
        return new ApiErrorDetails(null, fileCode, null, null, null, null, null);
    }

    /** 超限：附带被触发的限额名称与部署实际上限。 */
    public static ApiErrorDetails ofLimit(LimitName name, long maximum) {
        return new ApiErrorDetails(null, null, null, null, new Limit(name, maximum), null, null);
    }
}
