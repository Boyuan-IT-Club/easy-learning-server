package com.earlylearning.early_learning_server.license.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code IssuedLicense}：仅本次生成结果返回的可分发激活码。不得写日志。 */
public record IssuedLicenseResponse(@JsonProperty("id") int id,
                                    @JsonProperty("activation_code") String activationCode,
                                    @JsonProperty("status") String status) {

    @Override
    public String toString() {
        return "IssuedLicenseResponse[id=" + id + ", activationCode=REDACTED]";
    }
}
