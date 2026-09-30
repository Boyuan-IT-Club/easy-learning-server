package com.earlylearning.early_learning_server.teacher.domain;

import java.util.Optional;

import com.earlylearning.early_learning_server.common.secret.CrockfordCode;

/**
 * 账号恢复码：8 位 Crockford Base32（末位为校验位），展示为 {@code XXXX-XXXX}。
 *
 * <p>熵约 35 bit，单靠它不足以抵御穷举；安全性来自另外三道：24 小时有效、一次性、
 * 同一枚码累计错 5 次即作废（计数在数据库里，服务重启不清零）。
 *
 * @param value 规范形式（大写、无分隔符），用于哈希
 */
public record RecoveryCode(String value) {

    public static final int LENGTH = 8;

    public static RecoveryCode generate() {
        return new RecoveryCode(CrockfordCode.generate(LENGTH));
    }

    public static Optional<RecoveryCode> parse(String input) {
        return CrockfordCode.parse(input, LENGTH).map(RecoveryCode::new);
    }

    public String formatted() {
        return CrockfordCode.format(value);
    }

    @Override
    public String toString() {
        return "RecoveryCode[REDACTED]";
    }
}
