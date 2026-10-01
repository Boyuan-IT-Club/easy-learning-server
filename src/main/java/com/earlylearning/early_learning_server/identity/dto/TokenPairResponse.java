package com.earlylearning.early_learning_server.identity.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.identity.entity.TeacherAccount;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约 {@code TokenPair}：注册与刷新的返回。refresh_token 不设到期时间，所以没有 refresh_expires_at。
 * 含凭证明文：只进首次结果、短时重放缓存与刷新宽限，不得写日志。
 */
public record TokenPairResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("refresh_token") String refreshToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("access_expires_at") Instant accessExpiresAt,
                                @JsonProperty("user") UserAccountResponse user) {

    public static TokenPairResponse of(TeacherTokens tokens, TeacherAccount account) {
        return new TokenPairResponse(tokens.access().value(), tokens.refresh().value(), "Bearer",
                tokens.access().expiresAt(), UserAccountResponse.from(account));
    }

    @Override
    public String toString() {
        return "TokenPairResponse[tokens=REDACTED, user=" + user + "]";
    }
}
