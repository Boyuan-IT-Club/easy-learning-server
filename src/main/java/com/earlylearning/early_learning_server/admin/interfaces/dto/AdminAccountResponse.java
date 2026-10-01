package com.earlylearning.early_learning_server.admin.interfaces.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.admin.domain.AdminAccountSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code AdminAccount}。不含密码或密码哈希。 */
public record AdminAccountResponse(@JsonProperty("id") int id,
                                   @JsonProperty("username") String username,
                                   @JsonProperty("status") String status,
                                   @JsonProperty("created_at") Instant createdAt,
                                   @JsonProperty("updated_at") Instant updatedAt) {

    public static AdminAccountResponse from(AdminAccountSummary account) {
        return new AdminAccountResponse(account.id(), account.username(), account.status().name(),
                account.createdAt(), account.updatedAt());
    }
}
