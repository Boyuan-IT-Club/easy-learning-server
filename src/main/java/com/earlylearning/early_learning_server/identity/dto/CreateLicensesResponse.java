package com.earlylearning.early_learning_server.identity.dto;

import java.util.List;

import com.earlylearning.early_learning_server.identity.model.IssuedLicenseIds;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 createLicenses 的 data：{@code {items: [IssuedLicense]}}。含原码，只进首次结果与短时重放。 */
public record CreateLicensesResponse(@JsonProperty("items") List<IssuedLicenseResponse> items) {

    /** 脱敏快照：只有这批码的 id。 */
    public IssuedLicenseIds ids() {
        return new IssuedLicenseIds(items.stream().map(IssuedLicenseResponse::id).toList());
    }
}
