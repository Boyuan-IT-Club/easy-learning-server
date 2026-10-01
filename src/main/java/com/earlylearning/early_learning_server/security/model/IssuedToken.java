package com.earlylearning.early_learning_server.security.model;

import java.time.Instant;

/**
 * 一枚刚签发的 Token。
 *
 * @param value     明文，只在签发的那次响应里交给客户端
 * @param hash      SHA-256，服务端只保存它
 * @param expiresAt 到期时间；refresh_token 不设到期时间，为 null
 */
public record IssuedToken(String value, String hash, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
