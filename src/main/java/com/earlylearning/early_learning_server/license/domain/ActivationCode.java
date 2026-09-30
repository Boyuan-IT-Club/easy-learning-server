package com.earlylearning.early_learning_server.license.domain;

import java.util.Optional;

import com.earlylearning.early_learning_server.common.secret.CrockfordCode;

/**
 * 激活码：16 位 Crockford Base32，末位为校验位，展示为 {@code XXXX-XXXX-XXXX-XXXX}（PRD 2.2-1）。
 *
 * @param value 规范形式（大写、无分隔符），用于哈希
 */
public record ActivationCode(String value) {

    public static final int LENGTH = 16;

    public static ActivationCode generate() {
        return new ActivationCode(CrockfordCode.generate(LENGTH));
    }

    /** 容忍大小写、连字符与空白，以及 O/0、I/L/1 的混写；长度或校验位不对时为空。 */
    public static Optional<ActivationCode> parse(String input) {
        return CrockfordCode.parse(input, LENGTH).map(ActivationCode::new);
    }

    /** 末 4 位：让管理员核对发出去的纸条，不足以用于激活。 */
    public String hint() {
        return value.substring(LENGTH - 4);
    }

    public String formatted() {
        return CrockfordCode.format(value);
    }

    @Override
    public String toString() {
        return "ActivationCode[****" + hint() + "]";
    }
}
