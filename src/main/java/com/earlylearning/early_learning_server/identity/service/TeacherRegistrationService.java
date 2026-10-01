package com.earlylearning.early_learning_server.identity.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.identity.Usernames;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.entity.License;
import com.earlylearning.early_learning_server.identity.entity.TeacherAccount;
import com.earlylearning.early_learning_server.identity.entity.TeacherStatus;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;
import com.earlylearning.early_learning_server.security.service.TokenService;

/**
 * 教师首次注册并激活（契约 registerTeacher）。
 *
 * <p>同一事务：锁码 → 建号 → 占码 → 签发 Token 并写 refresh 哈希。任何一步失败整体回滚，激活码仍为 UNUSED。
 * 云端不收密码：教师密码只在平板本地哈希保存。成功结果短时内存重放，缓存丢失后返回 409
 * SENSITIVE_RESULT_EXPIRED，不重复消耗激活码。
 */
@Service
public class TeacherRegistrationService {

    public static final RateLimitRule PER_IP = new RateLimitRule("register", Duration.ofMinutes(1), 10);

    private final LicenseMapper licenses;
    private final TeacherAccountMapper teachers;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final SlidingWindowRateLimiter rateLimiter;
    private final KeyedHasher hasher;
    private final Clock clock;

    public TeacherRegistrationService(LicenseMapper licenses,
                                      TeacherAccountMapper teachers,
                                      TokenService tokenService,
                                      SensitiveIdempotency sensitiveIdempotency,
                                      SlidingWindowRateLimiter rateLimiter,
                                      KeyedHasher hasher,
                                      Clock clock) {
        this.licenses = licenses;
        this.teachers = teachers;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.rateLimiter = rateLimiter;
        this.hasher = hasher;
        this.clock = clock;
    }

    public TokenPairResponse register(String activationCode, String rawUsername, String idempotencyKey) {
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
                TokenPairResponse.class, () -> createAccount(activationCode, username),
                UserAccountResponse.class, TokenPairResponse::user);
    }

    private TokenPairResponse createAccount(String activationCode, String username) {
        // 锁码：并发注册同一个码时串行，后到者看到的已是 ACTIVE。激活码原样核对，不做规范化（契约）
        License license = licenses.selectByHashForUpdate(hasher.hash(activationCode));
        if (license == null) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        license.ensureClaimable();

        TeacherAccount account = insertTeacher(username);
        license.claimBy(account.getId(), clock.instant());
        if (licenses.markClaimed(license.getId(), account.getId(), license.getActivatedAt()) != 1) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }

        TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        teachers.updateRefreshHash(account.getId(), tokens.refresh().hash());
        return TokenPairResponse.of(tokens, account);
    }

    /** @param username 已规范化为小写 */
    private TeacherAccount insertTeacher(String username) {
        if (teachers.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        TeacherAccount account = new TeacherAccount();
        account.setUsername(username);
        account.setStatus(TeacherStatus.ENABLED);
        try {
            teachers.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 回读一次，拿到数据库生成的 created_at
        return teachers.selectById(account.getId());
    }
}
