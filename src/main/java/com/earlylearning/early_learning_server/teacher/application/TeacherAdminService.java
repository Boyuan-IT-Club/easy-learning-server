package com.earlylearning.early_learning_server.teacher.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.TargetType;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.teacher.domain.IssuedRecoveryCode;
import com.earlylearning.early_learning_server.teacher.domain.RecoveryCode;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherPage;
import com.earlylearning.early_learning_server.teacher.domain.TeacherStatus;
import com.earlylearning.early_learning_server.teacher.domain.TeacherSummary;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherQueryMapper;

/**
 * 后台对教师账号的操作：列表、详情、停用、启用、解绑设备、签发恢复码。
 *
 * <p>停用与解绑在事务提交后吊销该教师的全部 access_token，立即生效。
 * 停用不清空 refresh 哈希（重新启用后可直接恢复）；解绑会清空（原设备从此无法刷新）。
 */
@Service
public class TeacherAdminService {

    static final int MAX_REASON = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final TeacherAccountMapper mapper;
    private final TeacherQueryMapper queryMapper;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final KeyedHasher hasher;
    private final AuditLogService audit;
    private final TeacherProperties properties;
    private final Clock clock;

    public TeacherAdminService(TeacherAccountMapper mapper,
                               TeacherQueryMapper queryMapper,
                               TokenService tokenService,
                               SensitiveIdempotency sensitiveIdempotency,
                               KeyedHasher hasher,
                               AuditLogService audit,
                               TeacherProperties properties,
                               Clock clock) {
        this.mapper = mapper;
        this.queryMapper = queryMapper;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.hasher = hasher;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** @param status ACTIVE / DISABLED；为空时不过滤 */
    public TeacherPage page(TeacherStatus status, String keyword, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 1);
        Integer statusValue = status == null ? null : status.value();
        String pattern = likePattern(keyword);
        List<TeacherSummary> items = queryMapper.selectPage(statusValue, pattern, pageSize,
                (long) (pageNo - 1) * pageSize);
        return new TeacherPage(items, queryMapper.countMatching(statusValue, pattern), pageNo, pageSize);
    }

    public TeacherSummary detail(int teacherId) {
        TeacherSummary summary = queryMapper.selectById(teacherId);
        if (summary == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return summary;
    }

    @Transactional
    public void disable(int teacherId, String reason, int adminId) {
        String normalizedReason = requireReason(reason);
        TeacherAccount account = requireForUpdate(teacherId);
        if (account.getStatus() == TeacherStatus.DISABLED) {
            return;
        }
        account.disable(normalizedReason);
        mapper.updateStatus(teacherId, account.getStatus().value(), account.getDisabledReason());
        tokenService.revokeTeacherAfterCommit(teacherId);
        audit.record(AuditEntry.byAdmin(adminId, AuditAction.TEACHER_DISABLED, TargetType.TEACHER, teacherId)
                .withReason(normalizedReason));
    }

    @Transactional
    public void enable(int teacherId, int adminId) {
        TeacherAccount account = requireForUpdate(teacherId);
        if (account.getStatus() == TeacherStatus.ENABLED) {
            return;
        }
        account.enable();
        mapper.updateStatus(teacherId, account.getStatus().value(), account.getDisabledReason());
        audit.record(AuditEntry.byAdmin(adminId, AuditAction.TEACHER_ENABLED, TargetType.TEACHER, teacherId));
    }

    /** 受控解绑（PRD 2.2-7）：原设备从此无法刷新，数据由离线备份另行恢复，不经过云端。 */
    @Transactional
    public void unbindDevice(int teacherId, String reason, int adminId) {
        String normalizedReason = requireReason(reason);
        TeacherAccount account = requireForUpdate(teacherId);
        if (!account.isDeviceBound() && account.getRefreshTokenHash() == null) {
            return;
        }
        account.unbindDevice();
        mapper.updateBinding(teacherId, null, null, null);
        tokenService.revokeTeacherAfterCommit(teacherId);
        audit.record(AuditEntry.byAdmin(adminId, AuditAction.TEACHER_DEVICE_UNBOUND, TargetType.TEACHER, teacherId)
                .withReason(normalizedReason));
    }

    /** 签发恢复码：覆盖旧码，24 小时有效，只在本次结果里给出明文（10 分钟内可重放）。 */
    public IssuedRecoveryCode issueRecoveryCode(int teacherId, int adminId, String idempotencyKey) {
        String fingerprint = InputFingerprint.of(Integer.toString(teacherId), Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.TEACHER_RECOVERY_CODE, idempotencyKey, fingerprint,
                201, IssuedRecoveryCode.class,
                () -> {
                    TeacherAccount account = requireForUpdate(teacherId);
                    RecoveryCode code = RecoveryCode.generate();
                    Instant expiresAt = clock.instant().plus(properties.recoveryCodeTtl());
                    account.issueRecoveryCode(hasher.hash(code.value()), expiresAt);
                    mapper.updateRecoveryCode(teacherId, account.getRecoveryCodeHash(),
                            account.getRecoveryCodeExpiresAt(), account.getRecoveryCodeFailedCount());
                    audit.record(AuditEntry.byAdmin(adminId, AuditAction.TEACHER_RECOVERY_CODE_ISSUED,
                            TargetType.TEACHER, teacherId));
                    return new IssuedRecoveryCode(teacherId, code.formatted(), expiresAt);
                },
                IssuedRecoveryCode::redacted);
    }

    private TeacherAccount requireForUpdate(int teacherId) {
        TeacherAccount account = mapper.selectForUpdate(teacherId);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return account;
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > MAX_REASON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/reason"));
        }
        return normalized;
    }

    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
