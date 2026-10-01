package com.earlylearning.early_learning_server.admin.interfaces.dto;

import com.earlylearning.early_learning_server.common.web.RejectUnknownFields;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约：至少传 password / status 一项（{@code minProperties: 1}），未传字段保持原值。 */
@RejectUnknownFields
public record UpdateAdminAccountRequest(@JsonProperty("password") String password,
                                        @JsonProperty("status") String status) {

    @Override
    public String toString() {
        return "UpdateAdminAccountRequest[status=" + status + ", password=" + (password == null ? null : "REDACTED") + "]";
    }
}
