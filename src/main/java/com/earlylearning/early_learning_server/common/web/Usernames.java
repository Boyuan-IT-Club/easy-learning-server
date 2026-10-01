package com.earlylearning.early_learning_server.common.web;

import java.util.Locale;
import java.util.regex.Pattern;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 契约 {@code Username}：3–64 位 ASCII 字母、数字、下划线、点或连字符，服务端转换为小写后唯一。
 * 管理员与教师共用这一条规则。
 */
public final class Usernames {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_.-]{3,64}$");

    private Usernames() {
    }

    /** @throws BusinessException 400，field_path 为给定位置 */
    public static String normalize(String raw, String fieldPath) {
        if (raw == null || !FORMAT.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
        return raw.toLowerCase(Locale.ROOT);
    }
}
