package com.earlylearning.early_learning_server.auth.web;

import java.time.Instant;

import com.earlylearning.early_learning_server.auth.TokenService;
import com.earlylearning.early_learning_server.teacher.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.web.UserAccountResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约 {@code TokenPair}：注册与刷新的返回。refresh_token 不设到期时间，所以没有 refresh_expires_at。
 */
public record TokenPairResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("refresh_token") String refreshToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("access_expires_at") Instant accessExpiresAt,
                                @JsonProperty("user") UserAccountResponse user) {

    public static TokenPairResponse of(TokenService.TeacherTokens tokens, TeacherAccount account) {
        return new TokenPairResponse(tokens.access().value(), tokens.refresh().value(), "Bearer",
                tokens.access().expiresAt(), UserAccountResponse.from(account));
    }

    @Override
    public String toString() {
        return "TokenPairResponse[tokens=REDACTED, user=" + user + "]";
    }
}
