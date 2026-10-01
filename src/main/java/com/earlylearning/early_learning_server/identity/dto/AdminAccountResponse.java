package com.earlylearning.early_learning_server.identity.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.identity.entity.AdminAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code AdminAccount}。不含密码或密码哈希。 */
public record AdminAccountResponse(@JsonProperty("id") int id,
                                   @JsonProperty("username") String username,
                                   @JsonProperty("status") String status,
                                   @JsonProperty("created_at") Instant createdAt,
                                   @JsonProperty("updated_at") Instant updatedAt) {

    public static AdminAccountResponse from(AdminAccount account) {
        return new AdminAccountResponse(account.getId(), account.getUsername(), account.getStatus().name(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
