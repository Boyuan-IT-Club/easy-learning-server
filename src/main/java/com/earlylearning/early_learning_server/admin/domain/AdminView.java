package com.earlylearning.early_learning_server.admin.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 管理员的对外视图：不含密码哈希。也用作幂等快照。 */
public record AdminView(@JsonProperty("id") int id,
                        @JsonProperty("username") String username,
                        @JsonProperty("status") AdminStatus status,
                        @JsonProperty("created_at") Instant createdAt) {

    public static AdminView of(AdminAccount account) {
        return new AdminView(account.getId(), account.getUsername(), account.getStatus(), account.getCreatedAt());
    }
}
