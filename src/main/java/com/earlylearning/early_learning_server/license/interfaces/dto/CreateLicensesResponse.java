package com.earlylearning.early_learning_server.license.interfaces.dto;

import java.util.List;

import com.earlylearning.early_learning_server.license.domain.LicenseBatch;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 createLicenses 的 data：{@code {items: [IssuedLicense]}}。 */
public record CreateLicensesResponse(@JsonProperty("items") List<IssuedLicenseResponse> items) {

    public static CreateLicensesResponse from(LicenseBatch batch) {
        return new CreateLicensesResponse(batch.licenses().stream().map(IssuedLicenseResponse::from).toList());
    }
}
