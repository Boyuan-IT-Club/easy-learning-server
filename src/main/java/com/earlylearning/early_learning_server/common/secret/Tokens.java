package com.earlylearning.early_learning_server.common.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 不透明 Token 的生成与哈希。
 *
 * <p>Token 自带 256 bit 随机熵，SHA-256 足够，不需要慢哈希，也不需要 pepper。
 * 服务端只保存哈希；明文只出现在签发时的那一次响应里。
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    /** @param prefix 类型前缀，如 {@code at_}；用于一眼区分 Token 种类，防止拿错 */
    public static String generate(String prefix) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefix + ENCODER.encodeToString(bytes);
    }

    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }
}
