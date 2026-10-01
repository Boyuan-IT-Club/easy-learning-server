package com.earlylearning.early_learning_server.identity.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.identity.entity.TeacherAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code UserAccount}：只有云端账号元数据，没有密码、凭证哈希或儿童数据。 */
public record UserAccountResponse(@JsonProperty("id") int id,
                                  @JsonProperty("username") String username,
                                  @JsonProperty("status") int status,
                                  @JsonProperty("created_at") Instant createdAt) {

    public static UserAccountResponse from(TeacherAccount account) {
        return new UserAccountResponse(account.getId(), account.getUsername(), account.getStatus().value(),
                account.getCreatedAt());
    }
}
