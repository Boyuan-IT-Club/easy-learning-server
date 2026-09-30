package com.earlylearning.early_learning_server.admin.application;

import java.time.Duration;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.admin.domain.AdminLogin;
import com.earlylearning.early_learning_server.admin.domain.AdminView;
import com.earlylearning.early_learning_server.admin.infrastructure.AdminAccountMapper;
import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.ActorType;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.TargetType;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.auth.domain.IssuedToken;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.FailureLockout;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;

/**
 * 管理员登录、退出、当前身份、修改密码。
 *
 * <p>防暴力破解两道：同一 IP 每分钟最多 20 次登录请求；同一用户名 15 分钟内失败 5 次锁 15 分钟。
 * 用户名不存在与密码错误回同一个错误码，并且都执行一次 BCrypt 比对，避免通过响应内容或耗时判断账号是否存在。
 */
@Service
public class AdminSessionService {

    static final RateLimitRule LOGIN_PER_IP = new RateLimitRule("admin-login", Duration.ofMinutes(1), 20);
    static final RateLimitRule LOGIN_FAILURES = new RateLimitRule("admin-login-fail", Duration.ofMinutes(15), 5);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService audit;
    private final SlidingWindowRateLimiter rateLimiter;
    private final FailureLockout lockout;
    /** 用户名不存在时拿它比对，让两条失败路径的耗时一致。 */
    private final String dummyHash;

    public AdminSessionService(AdminAccountMapper mapper,
                               PasswordEncoder passwordEncoder,
                               TokenService tokenService,
                               AuditLogService audit,
                               SlidingWindowRateLimiter rateLimiter,
                               FailureLockout lockout) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.lockout = lockout;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-password");
    }

    public AdminLogin login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/username"));
        }
        if (password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/password"));
        }
        if (!rateLimiter.tryAcquire(LOGIN_PER_IP, RequestOrigin.clientIp())
                || lockout.lockedFor(LOGIN_FAILURES, username).isPresent()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        AdminAccount account = mapper.selectByUsername(username);
        boolean matches = passwordEncoder.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches) {
            lockout.recordFailure(LOGIN_FAILURES, username, LOCK_DURATION);
            audit.recordIndependently(new AuditEntry(ActorType.SYSTEM, account == null ? null : account.getId(),
                    AuditAction.ADMIN_LOGIN_FAILED, TargetType.ADMIN,
                    account == null ? null : account.getId().toString(), null,
                    Map.of("username", username), false));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 密码正确才透露"账号被停用"，否则等于告诉对方这个用户名存在
        account.ensureActive();
        lockout.recordSuccess(LOGIN_FAILURES, username);

        IssuedToken token = tokenService.issueAdmin(account.getId());
        audit.recordIndependently(AuditEntry.byAdmin(account.getId(), AuditAction.ADMIN_LOGIN,
                TargetType.ADMIN, account.getId()));
        return new AdminLogin(token, AdminView.of(account));
    }

    public void logout(AdminPrincipal principal) {
        tokenService.revokeAdminToken(principal.adminId(), principal.tokenHash());
    }

    public AdminView me(AdminPrincipal principal) {
        AdminAccount account = mapper.selectById(principal.adminId());
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return AdminView.of(account);
    }

    /** 修改自己的密码；成功后该管理员的其他 Token 全部失效，当前这一枚保留。 */
    @Transactional
    public void changePassword(AdminPrincipal principal, String oldPassword, String newPassword) {
        AdminAccount account = mapper.selectForUpdate(principal.adminId());
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        AdminAccount.checkPasswordPolicy(newPassword, "/new_password");
        mapper.updatePassword(account.getId(), passwordEncoder.encode(newPassword));
        tokenService.revokeAdminAfterCommit(account.getId(), principal.tokenHash());
        audit.record(AuditEntry.byAdmin(account.getId(), AuditAction.ADMIN_PASSWORD_CHANGED,
                TargetType.ADMIN, account.getId()));
    }
}
