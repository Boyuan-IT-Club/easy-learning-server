package com.earlylearning.early_learning_server.audit.interfaces.dto;

import com.earlylearning.early_learning_server.audit.domain.AuditLog;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 审计记录的管理端形状。时间为 UTC ISO 8601。 */
public record AuditLogResponse(
        @JsonProperty("id") long id,
        @JsonProperty("actor_type") String actorType,
        @JsonProperty("actor_id") Integer actorId,
        @JsonProperty("action") String action,
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id") String targetId,
        @JsonProperty("reason") String reason,
        @JsonProperty("detail") String detail,
        @JsonProperty("result") String result,
        @JsonProperty("ip") String ip,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("created_at") String createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorType().name(),
                log.getActorId(),
                log.getAction().name(),
                log.getTargetType() == null ? null : log.getTargetType().name(),
                log.getTargetId(),
                log.getReason(),
                log.getDetail(),
                log.getResult(),
                log.getIp(),
                log.getTraceId(),
                log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
    }
}
