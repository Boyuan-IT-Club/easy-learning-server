package com.earlylearning.early_learning_server.audit.domain;

import java.util.List;

/** 审计分页结果。 */
public record AuditPage(List<AuditLog> items, long total, int page, int size) {
}
