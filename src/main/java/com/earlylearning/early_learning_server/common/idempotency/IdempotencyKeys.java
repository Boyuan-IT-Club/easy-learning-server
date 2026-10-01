package com.earlylearning.early_learning_server.common.idempotency;

import java.util.regex.Pattern;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/** 契约：{@code Idempotency-Key} 为客户端生成的 UUID；缺失或格式不对返回 400。 */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private IdempotencyKeys() {
    }

    public static String require(String header) {
        if (header == null || !UUID.matcher(header).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/" + HEADER));
        }
        return header.toLowerCase(java.util.Locale.ROOT);
    }
}
