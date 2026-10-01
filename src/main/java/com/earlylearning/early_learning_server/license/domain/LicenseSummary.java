package com.earlylearning.early_learning_server.license.domain;

import java.time.Instant;

/** 激活码的只读快照：不含哈希。未激活时 {@code userId}、{@code activatedAt} 为 null。 */
public record LicenseSummary(int id, Integer userId, LicenseStatus status, Instant activatedAt) {

    public static LicenseSummary of(License license) {
        return new LicenseSummary(license.getId(), license.getUserId(), license.getStatus(), license.getActivatedAt());
    }
}
