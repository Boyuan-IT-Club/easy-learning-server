package com.earlylearning.early_learning_server.audit.domain;

import java.util.Map;

/**
 * 写审计时的入参。
 *
 * @param detail 非敏感补充信息，序列化为 JSON；不得包含码、Token、密码
 */
public record AuditEntry(ActorType actorType,
                         Integer actorId,
                         AuditAction action,
                         TargetType targetType,
                         String targetId,
                         String reason,
                         Map<String, Object> detail,
                         boolean success) {

    public static AuditEntry byAdmin(int adminId, AuditAction action, TargetType targetType, Object targetId) {
        return new AuditEntry(ActorType.ADMIN, adminId, action, targetType, str(targetId), null, null, true);
    }

    public static AuditEntry byTeacher(int teacherId, AuditAction action) {
        return new AuditEntry(ActorType.TEACHER, teacherId, action, TargetType.TEACHER, str(teacherId),
                null, null, true);
    }

    public static AuditEntry bySystem(AuditAction action, TargetType targetType, Object targetId) {
        return new AuditEntry(ActorType.SYSTEM, null, action, targetType, str(targetId), null, null, true);
    }

    public AuditEntry withReason(String value) {
        return new AuditEntry(actorType, actorId, action, targetType, targetId, value, detail, success);
    }

    public AuditEntry withDetail(Map<String, Object> value) {
        return new AuditEntry(actorType, actorId, action, targetType, targetId, reason, value, success);
    }

    public AuditEntry failed() {
        return new AuditEntry(actorType, actorId, action, targetType, targetId, reason, detail, false);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
