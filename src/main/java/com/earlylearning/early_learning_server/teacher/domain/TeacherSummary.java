package com.earlylearning.early_learning_server.teacher.domain;

import java.time.Instant;

import com.earlylearning.early_learning_server.license.domain.LicenseStatus;

/** 后台教师列表的一行（读模型）。不含任何教师资料：资料只存在平板上。 */
public record TeacherSummary(int id,
                             String username,
                             TeacherStatus status,
                             Integer licenseId,
                             String licenseCodeHint,
                             LicenseStatus licenseStatus,
                             String licenseRemark,
                             String deviceId,
                             Instant deviceBoundAt,
                             Instant lastRefreshAt,
                             Instant createdAt,
                             String disabledReason) {

    public boolean deviceBound() {
        return deviceId != null;
    }
}
