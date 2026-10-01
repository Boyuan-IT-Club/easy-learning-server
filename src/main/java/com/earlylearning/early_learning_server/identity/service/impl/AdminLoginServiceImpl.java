package com.earlylearning.early_learning_server.identity.service.impl;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.earlylearning.early_learning_server.identity.service.AdminLoginService;
import com.earlylearning.early_learning_server.security.service.TokenService;

/**
 * {@link AdminLoginService} 的实现。
 *
 * <ul>
 *   <li>用户名不存在时也跑一次哈希比对：两种失败耗时相同、错误码相同，不透露用户名是否存在。</li>
 *   <li>密码对了才判断停用：否则可以借"停用"提示探测哪些用户名存在。</li>
 * </ul>
 */
@Service
public class AdminLoginServiceImpl implements AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginServiceImpl.class);

    /** 同一 IP 每分钟最多 20 次登录请求。 */
    public static final RateLimitRule LOGIN_PER_IP = new RateLimitRule("admin-login-ip", Duration.ofMinutes(1), 20);

    /** 同一用户名 15 分钟内失败 5 次，锁定 15 分钟。 */
    public static final RateLimitRule LOGIN_FAILURES = new RateLimitRule("admin-login-fail", Duration.ofMinutes(15), 5);

    /** 连续失败达到上限后的锁定时长。 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final FailureLockout failureLockout;
    private final String dummyHash;

    public AdminLoginServiceImpl(AdminAccountMapper adminAccountMapper,
                                 PasswordEncoder passwordEncoder,
                                 TokenService tokenService,
                                 SlidingWindowRateLimiter slidingWindowRateLimiter,
                                 FailureLockout failureLockout) {
        this.adminAccountMapper = adminAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.failureLockout = failureLockout;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public AdminSessionResponse login(String rawUsername, String password) {
        if (!slidingWindowRateLimiter.tryAcquire(LOGIN_PER_IP, RequestOrigin.clientIp())) {
            log.warn("管理员登录触发 IP 限流 clientIp={}", RequestOrigin.clientIp());
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswordPolicy.require(password, "/password");
        if (failureLockout.lockedFor(LOGIN_FAILURES, username).isPresent()) {
            log.warn("管理员登录被拒：用户名处于锁定期 username={}", username);
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        AdminAccount account = adminAccountMapper.selectByUsername(username);
        boolean matches = passwordEncoder.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches) {
            if (failureLockout.recordFailure(LOGIN_FAILURES, username, LOCK_DURATION)) {
                log.warn("管理员连续登录失败达到上限，锁定 {} 分钟 username={}", LOCK_DURATION.toMinutes(), username);
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            log.warn("管理员登录失败：用户名或密码错误 username={} accountExists={}", username, account != null);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!account.isActive()) {
            log.warn("已停用的管理员尝试登录 adminId={}", account.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        failureLockout.recordSuccess(LOGIN_FAILURES, username);
        log.info("管理员登录成功 adminId={} username={}", account.getId(), username);
        return AdminSessionResponse.of(tokenService.issueAdmin(account.getId()), account);
    }
}
