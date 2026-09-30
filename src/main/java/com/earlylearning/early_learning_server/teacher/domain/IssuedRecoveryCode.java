package com.earlylearning.early_learning_server.teacher.domain;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员签发的恢复码（明文，格式化后的 {@code XXXX-XXXX}）。
 *
 * <p>落库的幂等快照用 {@link #redacted()}，不含明文。
 */
public record IssuedRecoveryCode(@JsonProperty("teacher_id") int teacherId,
                                 @JsonProperty("recovery_code") String recoveryCode,
                                 @JsonProperty("expires_at") Instant expiresAt) {

    public Map<String, Object> redacted() {
        return Map.of("teacher_id", teacherId, "expires_at", expiresAt.toString());
    }

    @Override
    public String toString() {
        return "IssuedRecoveryCode[teacherId=" + teacherId + ", recoveryCode=REDACTED]";
    }
}
