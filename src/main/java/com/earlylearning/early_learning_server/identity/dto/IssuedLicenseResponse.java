package com.earlylearning.early_learning_server.identity.dto;

import com.earlylearning.early_learning_server.common.enums.LicenseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code IssuedLicense}：仅本次生成结果返回的可分发激活码，状态恒为 UNUSED。不得写日志。 */
public record IssuedLicenseResponse(@JsonProperty("id") int id,
                                    @JsonProperty("activation_code") String activationCode,
                                    @JsonProperty("status") String status) {

    public static IssuedLicenseResponse unused(int id, String activationCode) {
        return new IssuedLicenseResponse(id, activationCode, LicenseStatus.UNUSED.name());
    }

    @Override
    public String toString() {
        return "IssuedLicenseResponse[id=" + id + ", activationCode=REDACTED]";
    }
}
