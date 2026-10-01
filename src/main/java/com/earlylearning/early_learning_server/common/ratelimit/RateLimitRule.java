package com.earlylearning.early_learning_server.common.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * 一条限流规则：在任意长度为 {@code window} 的时间段内最多允许 {@code limit} 次。
 *
 * @param name 规则名，进入 Redis 键，只用小写字母、数字与连字符
 */
public record RateLimitRule(String name, Duration window, int limit) {

    public RateLimitRule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(window, "window");
        if (!name.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("规则名只能包含小写字母、数字与连字符: " + name);
        }
        if (window.isNegative() || window.isZero() || limit < 1) {
            throw new IllegalArgumentException("窗口必须为正且上限至少为 1");
        }
    }
}
