package com.earlylearning.early_learning_server.identity.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.entity.AdminAccount;
import com.earlylearning.early_learning_server.security.model.IssuedToken;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code AdminSession}：token 明文只在这一次返回。无管理员刷新接口，过期重新登录。 */
public record AdminSessionResponse(@JsonProperty("token") String token,
                                   @JsonProperty("token_type") String tokenType,
                                   @JsonProperty("expires_at") Instant expiresAt,
                                   @JsonProperty("account") AdminAccountResponse account) {

    public static AdminSessionResponse of(IssuedToken token, AdminAccount account) {
        return new AdminSessionResponse(token.value(), "Bearer", token.expiresAt(), AdminAccountResponse.from(account));
    }

    @Override
    public String toString() {
        return "AdminSessionResponse[token=REDACTED, account=" + account + "]";
    }
}
