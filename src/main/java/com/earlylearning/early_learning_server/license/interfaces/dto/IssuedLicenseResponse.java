package com.earlylearning.early_learning_server.license.interfaces.dto;

import com.earlylearning.early_learning_server.license.domain.IssuedLicense;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code IssuedLicense}：仅本次生成结果返回的可分发激活码。不得写日志。 */
public record IssuedLicenseResponse(@JsonProperty("id") int id,
                                    @JsonProperty("activation_code") String activationCode,
                                    @JsonProperty("status") String status) {

    public static IssuedLicenseResponse from(IssuedLicense license) {
        return new IssuedLicenseResponse(license.id(), license.activationCode(), LicenseStatus.UNUSED.name());
    }

    @Override
    public String toString() {
        return "IssuedLicenseResponse[id=" + id + ", activationCode=REDACTED]";
    }
}
