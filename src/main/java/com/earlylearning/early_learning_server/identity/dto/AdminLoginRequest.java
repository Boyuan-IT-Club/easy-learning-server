package com.earlylearning.early_learning_server.identity.dto;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code additionalProperties: false}。密码不写日志。 */
@RejectUnknownFields
public record AdminLoginRequest(@JsonProperty("username") String username,
                                @JsonProperty("password") String password) {

    @Override
    public String toString() {
        return "AdminLoginRequest[username=" + username + ", password=REDACTED]";
    }
}
