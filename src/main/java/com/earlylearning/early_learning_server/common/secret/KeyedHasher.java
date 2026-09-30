package com.earlylearning.early_learning_server.common.secret;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * 激活码、恢复码的哈希：HMAC-SHA256(pepper)。
 *
 * <p>为什么不用 BCrypt：服务端要按码值查库（{@code activation_code_hash} 是唯一键），
 * BCrypt 每次结果不同，没法查。为什么要 pepper：码的熵有限（恢复码约 35 bit），
 * 数据库泄露后不加 pepper 可以离线穷举。
 */
@Component
public class KeyedHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public KeyedHasher(SecretProperties properties) {
        this.key = new SecretKeySpec(properties.codePepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** @param normalizedCode 已规范化的码（大写、无分隔符），保证同一个码只有一种哈希 */
    public String hash(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("运行环境不支持 " + ALGORITHM, e);
        }
    }
}
