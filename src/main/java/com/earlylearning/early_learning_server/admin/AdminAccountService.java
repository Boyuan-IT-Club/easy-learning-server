package com.earlylearning.early_learning_server.admin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.admin.web.AdminAccountPageResponse;
import com.earlylearning.early_learning_server.admin.web.AdminAccountResponse;
import com.earlylearning.early_learning_server.admin.web.AdminSessionResponse;
import com.earlylearning.early_learning_server.auth.IssuedToken;
import com.earlylearning.early_learning_server.auth.TokenService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.FailureLockout;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageParams;
import com.earlylearning.early_learning_server.common.web.Usernames;

/**
 * 管理员登录与账号维护（契约"管理员控制"）。所有管理员同权限，无 RBAC。
 *
 * <ul>
 *   <li>登录：同一 IP 每分钟 20 次；同一用户名 15 分钟内失败 5 次锁 15 分钟，都返回 429 RATE_LIMITED。
 *       错误凭据不透露用户名是否存在（不存在时也跑一次哈希，耗时一致）。</li>
 *   <li>改密码或停用：提交后删除该管理员已签发的全部 Token。</li>
 * </ul>
 */
@Service
public class AdminAccountService {

    static final RateLimitRule LOGIN_PER_IP = new RateLimitRule("admin-login-ip", Duration.ofMinutes(1), 20);
    static final RateLimitRule LOGIN_FAILURES = new RateLimitRule("admin-login-fail", Duration.ofMinutes(15), 5);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IdempotencyService idempotency;
    private final SlidingWindowRateLimiter rateLimiter;
    private final FailureLockout lockout;
    private final KeyedHasher hasher;
    /** 用户名不存在时拿它做一次比对，让两种失败耗时相同。 */
    private final String dummyHash;

    public AdminAccountService(AdminAccountMapper mapper,
                               PasswordEncoder passwordEncoder,
                               TokenService tokenService,
                               IdempotencyService idempotency,
                               SlidingWindowRateLimiter rateLimiter,
                               FailureLockout lockout,
                               KeyedHasher hasher) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.idempotency = idempotency;
        this.rateLimiter = rateLimiter;
        this.lockout = lockout;
        this.hasher = hasher;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public AdminSessionResponse login(String rawUsername, String password) {
        if (!rateLimiter.tryAcquire(LOGIN_PER_IP, RequestOrigin.clientIp())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswords.require(password, "/password");
        if (lockout.lockedFor(LOGIN_FAILURES, username).isPresent()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        AdminAccount account = mapper.selectByUsername(username);
        boolean matches = passwordEncoder.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches) {
            if (lockout.recordFailure(LOGIN_FAILURES, username, LOCK_DURATION)) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 密码对了才说"停用"：否则可以借此探测哪些用户名存在
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        lockout.recordSuccess(LOGIN_FAILURES, username);
        IssuedToken token = tokenService.issueAdmin(account.getId());
        return new AdminSessionResponse(token.value(), "Bearer", token.expiresAt(), AdminAccountResponse.from(account));
    }

    /** 仅已有管理员可创建；默认 ACTIVE。幂等快照不含密码（指纹里是密码的 HMAC）。 */
    @Transactional
    public StoredResponse create(String rawUsername, String password, String idempotencyKey) {
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswords.require(password, "/password");
        String fingerprint = InputFingerprint.of(username, hasher.hash(password));
        Optional<StoredResponse> replayed = idempotency.claim(IdempotencyScope.ADMIN_ACCOUNT_CREATE, idempotencyKey,
                fingerprint);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        AdminAccount created = insert(username, password);
        String body = idempotency.record(IdempotencyScope.ADMIN_ACCOUNT_CREATE, idempotencyKey, 201,
                ApiResponse.ok(AdminAccountResponse.from(created)));
        return new StoredResponse(201, body);
    }

    /** 部署初始化首个管理员时用；不走幂等。 */
    @Transactional
    public AdminAccount bootstrap(String rawUsername, String password) {
        return insert(Usernames.normalize(rawUsername, "admin.bootstrap.username"),
                AdminPasswords.require(password, "admin.bootstrap.password"));
    }

    public boolean anyExists() {
        return mapper.countAll() > 0;
    }

    public AdminAccountPageResponse list(PageParams page, String rawUsername, AdminStatus status) {
        String username = rawUsername == null ? null : Usernames.normalize(rawUsername, "/parameters/username");
        String statusValue = status == null ? null : status.name();
        List<AdminAccountResponse> items = mapper.selectPage(username, statusValue, page.pageSize(), page.offset())
                .stream().map(AdminAccountResponse::from).toList();
        long total = mapper.countMatching(username, statusValue);
        return new AdminAccountPageResponse(items, page.page(), page.pageSize(), total);
    }

    /**
     * 至少一项；未传字段保持原值。改密码或停用后，该管理员已签发的 Token 全部失效。
     * 重复提交相同目标状态不产生额外变化。
     */
    @Transactional
    public AdminAccountResponse update(int id, String password, String rawStatus) {
        if (password == null && rawStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (password != null) {
            AdminPasswords.require(password, "/password");
        }
        AdminStatus target = rawStatus == null ? null : AdminStatus.parse(rawStatus, "/status");

        AdminAccount account = mapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean revokeTokens = false;
        if (password != null) {
            mapper.updatePassword(id, passwordEncoder.encode(password));
            revokeTokens = true;
        }
        if (target != null && target != account.getStatus()) {
            mapper.updateStatus(id, target.name());
            revokeTokens |= target == AdminStatus.DISABLED;
        }
        if (revokeTokens) {
            tokenService.revokeAdminAfterCommit(id);
        }
        // 回读：拿到 ON UPDATE 刷新后的 updated_at
        return AdminAccountResponse.from(mapper.selectById(id));
    }

    /** 管理端每个请求都要确认账号仍是 ACTIVE（契约 AdminBearer）。 */
    public AdminAccount find(int id) {
        return mapper.selectById(id);
    }

    private AdminAccount insert(String username, String password) {
        if (mapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AdminStatus.ACTIVE);
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        return mapper.selectById(account.getId());
    }
}
