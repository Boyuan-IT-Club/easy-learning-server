package com.earlylearning.early_learning_server.license.domain;

import java.time.Instant;

/** 激活码列表的一行（读模型），附带绑定教师的用户名。 */
public record LicenseSummary(int id,
                             String codeHint,
                             LicenseStatus status,
                             String remark,
                             Integer userId,
                             String username,
                             Instant createdAt,
                             Instant activatedAt,
                             Instant revokedAt,
                             String revokeReason) {
}
