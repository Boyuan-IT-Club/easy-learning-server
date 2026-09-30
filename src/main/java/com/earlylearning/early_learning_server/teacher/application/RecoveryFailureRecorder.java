package com.earlylearning.early_learning_server.teacher.application;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 在独立事务里记一次恢复码错误。
 *
 * <p>为什么必须独立：调用方随后会抛 RECOVERY_CODE_INVALID，外层事务回滚；
 * 计数若在外层事务里，就会跟着回滚，"错 5 次作废"永远不会生效。
 * 单独成一个 Bean，是为了让 {@code REQUIRES_NEW} 经过代理真正生效。
 */
@Component
class RecoveryFailureRecorder {

    private final TeacherAccountMapper mapper;
    private final AuditLogService audit;
    private final TeacherProperties properties;

    RecoveryFailureRecorder(TeacherAccountMapper mapper, AuditLogService audit, TeacherProperties properties) {
        this.mapper = mapper;
        this.audit = audit;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(int teacherId) {
        TeacherAccount account = mapper.selectForUpdate(teacherId);
        if (account == null) {
            return;
        }
        boolean exhausted = account.recordRecoveryFailure(properties.recoveryMaxFailures());
        mapper.updateRecoveryCode(account.getId(), account.getRecoveryCodeHash(),
                account.getRecoveryCodeExpiresAt(), account.getRecoveryCodeFailedCount());
        audit.record(AuditEntry.byTeacher(account.getId(), AuditAction.TEACHER_RECOVERY_FAILED)
                .withDetail(Map.of("failed_count", account.getRecoveryCodeFailedCount(), "exhausted", exhausted))
                .failed());
    }
}
