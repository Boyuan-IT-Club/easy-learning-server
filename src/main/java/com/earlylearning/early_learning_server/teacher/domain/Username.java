package com.earlylearning.early_learning_server.teacher.domain;

import java.util.regex.Pattern;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 教师用户名：4–32 位字母、数字、下划线、点、连字符，区分大小写（V1 排序规则为 utf8mb4_0900_bin）。
 */
public record Username(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_.-]{4,32}$");

    /** @throws BusinessException 400，details.field_path = /username */
    public static Username parse(String raw) {
        if (raw == null || !FORMAT.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/username"));
        }
        return new Username(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
