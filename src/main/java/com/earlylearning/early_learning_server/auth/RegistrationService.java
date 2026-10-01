package com.earlylearning.early_learning_server.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.auth.web.TokenPairResponse;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.Usernames;
import com.earlylearning.early_learning_server.license.License;
import com.earlylearning.early_learning_server.license.LicenseLookup;
import com.earlylearning.early_learning_server.teacher.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.TeacherAccountService;

/**
 * 教师首次注册并激活（契约 registerTeacher）。
 *
 * <p>同一事务：锁码 → 建号 → 占码 → 签发 Token 并写 refresh 哈希。任何一步失败整体回滚，激活码仍为 UNUSED。
 * 云端不收密码：教师密码只在平板本地哈希保存。成功响应短时内存重放，缓存丢失后返回 409
 * SENSITIVE_RESULT_EXPIRED，不重复消耗激活码。
 */
@Service
public class RegistrationService {

    static final RateLimitRule PER_IP = new RateLimitRule("register", Duration.ofMinutes(1), 10);

    private final LicenseLookup licenses;
    private final TeacherAccountService teachers;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final SlidingWindowRateLimiter rateLimiter;
    private final KeyedHasher hasher;

    public RegistrationService(LicenseLookup licenses,
                               TeacherAccountService teachers,
                               TokenService tokenService,
                               SensitiveIdempotency sensitiveIdempotency,
                               SlidingWindowRateLimiter rateLimiter,
                               KeyedHasher hasher) {
        this.licenses = licenses;
        this.teachers = teachers;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.rateLimiter = rateLimiter;
        this.hasher = hasher;
    }

    public StoredResponse register(String activationCode, String rawUsername, String idempotencyKey) {
        if (!rateLimiter.tryAcquire(PER_IP, RequestOrigin.clientIp())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        if (activationCode == null || activationCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/activation_code"));
        }
        String username = Usernames.normalize(rawUsername, "/username");
        // 指纹里放码的 HMAC 而不是原码：idempotency_record 会落库
        String fingerprint = InputFingerprint.of(hasher.hash(activationCode), username);
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REGISTER, idempotencyKey, fingerprint, 201,
                () -> createAccount(activationCode, username),
                ApiResponse::ok,
                pair -> ApiResponse.ok(Map.of("user", pair.user())));
    }

    private TokenPairResponse createAccount(String activationCode, String username) {
        License license = licenses.lockAvailable(activationCode);
        TeacherAccount account = teachers.create(username);
        licenses.claim(license, account.getId());
        TokenService.TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        teachers.saveRefreshHash(account.getId(), tokens.refresh().hash());
        return TokenPairResponse.of(tokens, account);
    }
}
