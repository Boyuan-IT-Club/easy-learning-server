package com.earlylearning.early_learning_server.admin.domain;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 管理员密码规则（契约 {@code AdminPassword}）：8—128 字符，按码点计长度，不裁剪空白，服务端只存哈希。
 */
public final class AdminPasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private AdminPasswordPolicy() {
    }

    /** @throws BusinessException 400，field_path 为给定位置 */
    public static String require(String password, String fieldPath) {
        if (password == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
        return password;
    }
}
