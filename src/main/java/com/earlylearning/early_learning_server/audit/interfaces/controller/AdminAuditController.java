package com.earlylearning.early_learning_server.audit.interfaces.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditQuery;
import com.earlylearning.early_learning_server.audit.domain.TargetType;
import com.earlylearning.early_learning_server.audit.interfaces.dto.AuditPageResponse;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

/** 管控审计查询。只读，没有删除接口。权限：管理员（由安全链保证）。 */
@RestController
public class AdminAuditController {

    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<AuditPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(name = "target_type", required = false) TargetType targetType,
            @RequestParam(name = "target_id", required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(AuditPageResponse.from(auditLogService.query(
                new AuditQuery(action, targetType, targetId, from, to, page, pageSize))));
    }
}
