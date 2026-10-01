package com.earlylearning.early_learning_server.identity.dto;

import java.time.Instant;

import com.earlylearning.early_learning_server.identity.entity.License;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约 {@code License}：不含原码与哈希。{@code user_id}、{@code activated_at} 未激活时为 null，但必须出现。 */
public record LicenseResponse(@JsonProperty("id") int id,
                              @JsonInclude(JsonInclude.Include.ALWAYS) @JsonProperty("user_id") Integer userId,
                              @JsonProperty("status") String status,
                              @JsonInclude(JsonInclude.Include.ALWAYS) @JsonProperty("activated_at") Instant activatedAt) {

    public static LicenseResponse from(License license) {
        return new LicenseResponse(license.getId(), license.getUserId(), license.getStatus().name(),
                license.getActivatedAt());
    }
}
