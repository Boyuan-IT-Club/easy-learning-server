package com.earlylearning.early_learning_server.admin.application;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.admin.domain.AdminStatus;
import com.earlylearning.early_learning_server.admin.domain.AdminView;
import com.earlylearning.early_learning_server.admin.infrastructure.AdminAccountMapper;
import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.TargetType;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 管理员维护：列表、新建、停用、启用、重置密码。
 *
 * <p>不变量：不能停用自己；系统里至少保留一个 ACTIVE 管理员（否则再也没人能登录后台）。
 */
@Service
public class AdminAccountService {

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService audit;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public AdminAccountService(AdminAccountMapper mapper,
                               PasswordEncoder passwordEncoder,
                               TokenService tokenService,
                               AuditLogService audit,
                               IdempotencyService idempotency,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.audit = audit;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public List<AdminView> list() {
        return mapper.selectAllOrdered().stream().map(AdminView::of).toList();
    }

    public AdminView create(String username, String initialPassword, int actorId, String idempotencyKey) {
        AdminAccount.checkUsername(username, "/username");
        AdminAccount.checkPasswordPolicy(initialPassword, "/initial_password");
        // 指纹不含密码明文：同一个键换了密码重试，会被当成"输入已改变"而拒绝，这正是想要的
        String fingerprint = InputFingerprint.of(username, InputFingerprint.sha256Hex(initialPassword),
                Integer.toString(actorId));
        return transaction.execute(status -> {
            Optional<StoredResponse> replayed = idempotency.claim(IdempotencyScope.ADMIN_CREATE,
                    idempotencyKey, fingerprint);
            if (replayed.isPresent()) {
                return objectMapper.readValue(replayed.get().body(), AdminView.class);
            }
            AdminAccount account = AdminAccount.create(username, passwordEncoder.encode(initialPassword));
            try {
                mapper.insert(account);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
            AdminView view = AdminView.of(mapper.selectById(account.getId()));
            idempotency.record(IdempotencyScope.ADMIN_CREATE, idempotencyKey, 201, view);
            audit.record(AuditEntry.byAdmin(actorId, AuditAction.ADMIN_CREATED, TargetType.ADMIN, account.getId()));
            return view;
        });
    }

    @Transactional
    public void disable(int targetId, int actorId) {
        if (targetId == actorId) {
            throw new BusinessException(ErrorCode.ADMIN_LAST_ACTIVE, "不能停用自己");
        }
        AdminAccount target = requireForUpdate(targetId);
        if (!target.isActive()) {
            return;
        }
        if (mapper.countActiveForUpdate() <= 1) {
            throw new BusinessException(ErrorCode.ADMIN_LAST_ACTIVE);
        }
        mapper.updateStatus(targetId, AdminStatus.DISABLED.name());
        tokenService.revokeAdminAfterCommit(targetId, null);
        audit.record(AuditEntry.byAdmin(actorId, AuditAction.ADMIN_DISABLED, TargetType.ADMIN, targetId));
    }

    @Transactional
    public void enable(int targetId, int actorId) {
        AdminAccount target = requireForUpdate(targetId);
        if (target.isActive()) {
            return;
        }
        mapper.updateStatus(targetId, AdminStatus.ACTIVE.name());
        audit.record(AuditEntry.byAdmin(actorId, AuditAction.ADMIN_ENABLED, TargetType.ADMIN, targetId));
    }

    /** 重置他人密码；被重置者的全部 Token 失效。重置自己请走修改密码接口。 */
    @Transactional
    public void resetPassword(int targetId, String newPassword, int actorId) {
        AdminAccount.checkPasswordPolicy(newPassword, "/new_password");
        requireForUpdate(targetId);
        mapper.updatePassword(targetId, passwordEncoder.encode(newPassword));
        tokenService.revokeAdminAfterCommit(targetId, null);
        audit.record(AuditEntry.byAdmin(actorId, AuditAction.ADMIN_PASSWORD_RESET, TargetType.ADMIN, targetId));
    }

    private AdminAccount requireForUpdate(int id) {
        AdminAccount account = mapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return account;
    }
}
