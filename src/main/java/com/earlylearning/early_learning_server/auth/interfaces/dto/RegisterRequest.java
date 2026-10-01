package com.earlylearning.early_learning_server.auth.interfaces.dto;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约：只收 activation_code 与 username，<b>禁止提交 password 或 password_hash</b>；
 * {@code additionalProperties: false}，多带任何字段都 400。
 */
@RejectUnknownFields
public record RegisterRequest(@JsonProperty("activation_code") String activationCode,
                              @JsonProperty("username") String username) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", activationCode=REDACTED]";
    }
}
