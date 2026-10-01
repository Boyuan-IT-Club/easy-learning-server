package com.earlylearning.early_learning_server.identity.service;

import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.identity.Usernames;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.FailureLockout;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.entity.AdminAccount;
import com.earlylearning.early_learning_server.identity.dto.AdminSessionResponse;
import com.earlylearning.early_learning_server.identity.mapper.AdminAccountMapper;
import com.earlylearning.early_learning_server.identity.model.AdminPasswordPolicy;
import com.earlylearning.early_learning_server.security.service.TokenService;

/**
 * 管理员登录（契约 adminLogin）。内部管理员，无公开注册；无刷新接口，过期重新登录。
 *
 * <ul>
 *   <li>限流：同一 IP 每分钟 20 次；同一用户名 15 分钟内失败 5 次锁 15 分钟，都返回 429 RATE_LIMITED。</li>
 *   <li>错误凭据不透露用户名是否存在：不存在时也跑一次哈希比对，两种失败耗时相同、错误码相同。</li>
 *   <li>密码对了才判断停用：否则可以借"停用"提示探测哪些用户名存在。</li>
 * </ul>
 */
@Service
public class AdminLoginService {

    public static final RateLimitRule LOGIN_PER_IP = new RateLimitRule("admin-login-ip", Duration.ofMinutes(1), 20);
    public static final RateLimitRule LOGIN_FAILURES = new RateLimitRule("admin-login-fail", Duration.ofMinutes(15), 5);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SlidingWindowRateLimiter rateLimiter;
    private final FailureLockout lockout;
    private final String dummyHash;

    public AdminLoginService(AdminAccountMapper mapper,
                             PasswordEncoder passwordEncoder,
                             TokenService tokenService,
                             SlidingWindowRateLimiter rateLimiter,
                             FailureLockout lockout) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.lockout = lockout;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public AdminSessionResponse login(String rawUsername, String password) {
        if (!rateLimiter.tryAcquire(LOGIN_PER_IP, RequestOrigin.clientIp())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswordPolicy.require(password, "/password");
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
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        lockout.recordSuccess(LOGIN_FAILURES, username);
        return AdminSessionResponse.of(tokenService.issueAdmin(account.getId()), account);
    }
}
