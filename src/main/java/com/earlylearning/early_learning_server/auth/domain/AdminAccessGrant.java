package com.earlylearning.early_learning_server.auth.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 一枚管理员 Token 在 Redis 里对应的内容。 */
public record AdminAccessGrant(@JsonProperty("admin_id") int adminId,
                               @JsonProperty("expires_at") Instant expiresAt) {
}
