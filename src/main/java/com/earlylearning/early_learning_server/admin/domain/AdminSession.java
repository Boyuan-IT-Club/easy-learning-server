package com.earlylearning.early_learning_server.admin.domain;

import java.time.Instant;

/** 一次登录的结果（契约 {@code AdminSession}）：token 明文只在这一次返回。无刷新，过期重新登录。 */
public record AdminSession(String token, Instant expiresAt, AdminAccountSummary account) {

    @Override
    public String toString() {
        return "AdminSession[token=REDACTED, account=" + account + "]";
    }
}
