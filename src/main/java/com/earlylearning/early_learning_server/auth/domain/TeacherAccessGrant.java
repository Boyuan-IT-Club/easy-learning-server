package com.earlylearning.early_learning_server.auth.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 一枚教师 access_token 在 Redis 里对应的内容。 */
public record TeacherAccessGrant(@JsonProperty("user_id") int userId,
                                 @JsonProperty("device_id") String deviceId,
                                 @JsonProperty("expires_at") Instant expiresAt) {
}
