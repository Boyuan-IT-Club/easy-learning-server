package com.earlylearning.early_learning_server.admin.interfaces.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.admin.domain.AdminSession;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code AdminSession}。无管理员刷新接口，过期重新登录。 */
public record AdminSessionResponse(@JsonProperty("token") String token,
                                   @JsonProperty("token_type") String tokenType,
                                   @JsonProperty("expires_at") Instant expiresAt,
                                   @JsonProperty("account") AdminAccountResponse account) {

    public static AdminSessionResponse from(AdminSession session) {
        return new AdminSessionResponse(session.token(), "Bearer", session.expiresAt(),
                AdminAccountResponse.from(session.account()));
    }

    @Override
    public String toString() {
        return "AdminSessionResponse[token=REDACTED, account=" + account + "]";
    }
}
