package com.earlylearning.early_learning_server.common.secret;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * 激活码的哈希：HMAC-SHA256(pepper)。
 *
 * <p>为什么不用 BCrypt：服务端要按码值查库（{@code activation_code_hash} 是唯一键），
 * BCrypt 每次结果不同，没法查。为什么要 pepper：数据库泄露后，没有 pepper 就无法离线验证猜测的码。
 */
@Component
public class KeyedHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public KeyedHasher(SecretProperties properties) {
        this.key = new SecretKeySpec(properties.codePepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** @param code 原样的码（契约要求原样校验，不在服务端规范化） */
    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("运行环境不支持 " + ALGORITHM, e);
        }
    }
}
