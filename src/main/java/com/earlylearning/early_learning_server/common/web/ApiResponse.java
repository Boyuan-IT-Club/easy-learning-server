package com.earlylearning.early_learning_server.common.web;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应包络。
 *
 * <p>契约约束（逐条来自 OpenAPI 的 {@code *Response} schema）：
 * <ul>
 *   <li>{@code code} / {@code message} / {@code data} 三者恒为 required；</li>
 *   <li>失败时 {@code data} 必须为 null，<b>且必须出现在 JSON 里</b>；</li>
 *   <li>成功响应的 schema 中没有 {@code details} 属性，并声明
 *       {@code additionalProperties: false}，因此成功时不能出现 details 键；</li>
 *   <li>{@code message} 不得包含输入文本、密码或 Token。</li>
 * </ul>
 *
 * <p>本类不携带 HTTP 状态：状态由抛出位置的异常类型（或 Controller 的返回值）决定。
 *
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(

        String code,
        String message,

        /* 始终序列化：失败时必须是显式的 null，不能被 NON_NULL 省略 */
        @JsonInclude(JsonInclude.Include.ALWAYS)
        T data,

        /* 仅有值时出现：成功响应带 details 会违反 additionalProperties:false */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiErrorDetails details
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(ErrorCode.OK.name(), message, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return new ApiResponse<>(code.name(), code.defaultMessage(), null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(code.name(), message, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message, ApiErrorDetails details) {
        return new ApiResponse<>(code.name(), message, null, details);
    }
}
