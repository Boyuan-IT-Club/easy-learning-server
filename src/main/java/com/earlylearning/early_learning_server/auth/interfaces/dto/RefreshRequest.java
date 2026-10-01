package com.earlylearning.early_learning_server.auth.interfaces.dto;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code additionalProperties: false}。refresh_token 不得写日志。 */
@RejectUnknownFields
public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[REDACTED]";
    }
}
