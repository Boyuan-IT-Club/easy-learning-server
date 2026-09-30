package com.earlylearning.early_learning_server.teacher.interfaces.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherSession;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 教师端 {@code /api/auth/**} 的请求与响应形状。
 *
 * <p>含凭证的 record 覆盖 {@code toString()}，避免被框架的调试日志打出明文。
 */
public final class TeacherAuthDtos {

    private TeacherAuthDtos() {
    }

    public record VerifyLicenseRequest(@JsonProperty("activation_code") String activationCode) {
        @Override
        public String toString() {
            return "VerifyLicenseRequest[REDACTED]";
        }
    }

    public record VerifyLicenseResponse(@JsonProperty("available") boolean available) {
    }

    public record RegisterRequest(@JsonProperty("activation_code") String activationCode,
                                  @JsonProperty("username") String username) {
        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + ", activationCode=REDACTED]";
        }
    }

    public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequest[REDACTED]";
        }
    }

    public record RecoverRequest(@JsonProperty("username") String username,
                                 @JsonProperty("recovery_code") String recoveryCode) {
        @Override
        public String toString() {
            return "RecoverRequest[username=" + username + ", recoveryCode=REDACTED]";
        }
    }

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("access_token_expires_at") Instant accessTokenExpiresAt,
                                @JsonProperty("refresh_token") String refreshToken) {

        public static TokenResponse from(TokenPair pair) {
            return new TokenResponse(pair.access().value(), pair.access().expiresAt(), pair.refresh().value());
        }

        @Override
        public String toString() {
            return "TokenResponse[REDACTED]";
        }
    }

    /** 注册与恢复的响应。注册时 device_rebound 恒为 false。 */
    public record SessionResponse(@JsonProperty("user_id") int userId,
                                  @JsonProperty("username") String username,
                                  @JsonProperty("device_rebound") boolean deviceRebound,
                                  @JsonProperty("access_token") String accessToken,
                                  @JsonProperty("access_token_expires_at") Instant accessTokenExpiresAt,
                                  @JsonProperty("refresh_token") String refreshToken) {

        public static SessionResponse from(TeacherSession session) {
            TokenPair tokens = session.tokens();
            return new SessionResponse(session.userId(), session.username(), session.deviceRebound(),
                    tokens.access().value(), tokens.access().expiresAt(), tokens.refresh().value());
        }

        @Override
        public String toString() {
            return "SessionResponse[userId=" + userId + ", tokens=REDACTED]";
        }
    }

    public record MeResponse(@JsonProperty("user_id") int userId,
                             @JsonProperty("username") String username,
                             @JsonProperty("account_status") String accountStatus) {

        public static MeResponse from(TeacherAccount account) {
            return new MeResponse(account.getId(), account.getUsername(), account.getStatus().display());
        }
    }
}
