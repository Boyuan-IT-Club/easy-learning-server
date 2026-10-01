package com.earlylearning.early_learning_server.auth.interfaces.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.auth.domain.TeacherTokenPair;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccountSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约 {@code TokenPair}：注册与刷新的返回。refresh_token 不设到期时间，所以没有 refresh_expires_at。
 */
public record TokenPairResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("refresh_token") String refreshToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("access_expires_at") Instant accessExpiresAt,
                                @JsonProperty("user") User user) {

    public static TokenPairResponse from(TeacherTokenPair pair) {
        return new TokenPairResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.accessExpiresAt(),
                User.from(pair.user()));
    }

    @Override
    public String toString() {
        return "TokenPairResponse[tokens=REDACTED, user=" + user + "]";
    }

    /** 契约 {@code UserAccount}。与 teacher 模块的同名响应字段相同；各模块的 DTO 留在各自的 interfaces 层。 */
    public record User(@JsonProperty("id") int id,
                       @JsonProperty("username") String username,
                       @JsonProperty("status") int status,
                       @JsonProperty("created_at") Instant createdAt) {

        static User from(TeacherAccountSummary account) {
            return new User(account.id(), account.username(), account.status().value(), account.createdAt());
        }
    }
}
