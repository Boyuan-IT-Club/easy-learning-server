package com.earlylearning.early_learning_server.ai.controller.validation;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 请求字段的通用校验：错误一律带字段路径，便于客户端定位。
 *
 * <p>包内可见：只服务于本包的几个请求校验器，不对外暴露。
 */
final class RequestFieldChecks {

    private RequestFieldChecks() {
    }

    /** 空值（含空字符串与纯空白）都不合法，用于必填的文本字段。 */
    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
    }

    /** 必填但可以是空字符串的字段用这个：{@code confirmed_text} 空串表示"已确认无回应"。 */
    static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw invalid(field);
        }
    }

    static void requireTrue(Boolean value, String field) {
        if (!Boolean.TRUE.equals(value)) {
            throw invalid(field);
        }
    }

    static BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/" + field));
    }
}
