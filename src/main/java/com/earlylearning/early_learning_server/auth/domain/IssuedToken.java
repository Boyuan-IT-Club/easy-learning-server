package com.earlylearning.early_learning_server.auth.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一枚刚签发的 Token。
 *
 * @param value     明文，只在签发的那次响应里交给客户端
 * @param hash      SHA-256，服务端只保存它
 * @param expiresAt 到期时间；refresh_token 不设到期时间，为 null
 */
public record IssuedToken(@JsonProperty("value") String value,
                          @JsonProperty("hash") String hash,
                          @JsonProperty("expires_at") Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
