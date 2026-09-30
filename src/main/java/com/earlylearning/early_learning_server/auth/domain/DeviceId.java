package com.earlylearning.early_learning_server.auth.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 平板的设备号：客户端首次启动时生成的 UUIDv4，每个 {@code /api} 请求放在 {@code X-Device-Id} 头里。
 *
 * <p>不用 ANDROID_ID：它随签名与用户变化，恢复出厂后也会变，不适合当绑定依据。
 * 设备号本身可以伪造，它的作用是把 Token 绑定到一台设备上，不是独立的安全边界。
 */
public record DeviceId(String value) {

    public static final String HEADER = "X-Device-Id";

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /** @return 规范形式（小写）；格式不对时为空 */
    public static Optional<DeviceId> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return UUID.matcher(normalized).matches() ? Optional.of(new DeviceId(normalized)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
