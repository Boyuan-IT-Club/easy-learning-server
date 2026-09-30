package com.earlylearning.early_learning_server.audit.domain;

import java.time.Instant;

/** 审计查询条件；为空的条件不参与过滤。时间为 UTC。 */
public record AuditQuery(AuditAction action,
                         TargetType targetType,
                         String targetId,
                         Instant from,
                         Instant to,
                         int page,
                         int size) {
}
