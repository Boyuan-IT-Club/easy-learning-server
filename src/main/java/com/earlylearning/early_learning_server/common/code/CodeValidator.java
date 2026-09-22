package com.earlylearning.early_learning_server.common.code;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 公共 Code 的机械格式校验，不查询数据库，不生成或归一化 Code。
 */
public final class CodeValidator implements ConstraintValidator<ValidCode, String> {

    public static final int MAX_LENGTH = 64;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 可选引用允许 null；必填字段由调用方叠加 @NotNull。
        if (value == null) {
            return true;
        }
        // 与 utf8mb4 VARCHAR(64) 的字符长度一致，补充平面字符不按两个 UTF-16 单元计数。
        return !value.isBlank() && value.codePointCount(0, value.length()) <= MAX_LENGTH;
    }
}

