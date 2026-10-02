package com.earlylearning.early_learning_server.identity.service.impl;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.enums.TeacherStatus;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.identity.Usernames;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.entity.License;
import com.earlylearning.early_learning_server.entity.TeacherAccount;
import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.identity.service.TeacherRegistrationService;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;
import com.earlylearning.early_learning_server.security.service.TokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TeacherRegistrationService} 的实现。
 *
 * <p>同一事务：锁码 → 建号 → 占码 → 签发 Token 并写 refresh 哈希。幂等指纹里放激活码的 HMAC，不放原码。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherRegistrationServiceImpl implements TeacherRegistrationService {

    /** 同一 IP 每分钟最多 10 次注册请求。 */
    public static final RateLimitRule PER_IP = new RateLimitRule("register", Duration.ofMinutes(1), 10);

    private final LicenseMapper licenseMapper;
    private final TeacherAccountMapper teacherAccountMapper;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final KeyedHasher keyedHasher;
    private final Clock clock;

    @Override
    public TokenPairResponse register(String activationCode, String rawUsername, String idempotencyKey) {
        if (!slidingWindowRateLimiter.tryAcquire(PER_IP, RequestOrigin.clientIp())) {
            log.warn("教师注册触发 IP 限流 clientIp={}", RequestOrigin.clientIp());
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        if (activationCode == null || activationCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/activation_code"));
        }
        String username = Usernames.normalize(rawUsername, "/username");
        // 指纹里放码的 HMAC 而不是原码：idempotency_record 会落库
        String fingerprint = InputFingerprint.of(keyedHasher.hash(activationCode), username);
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REGISTER, idempotencyKey, fingerprint, 201,
                TokenPairResponse.class, () -> createAccount(activationCode, username),
                UserAccountResponse.class, TokenPairResponse::user);
    }

    private TokenPairResponse createAccount(String activationCode, String username) {
        // 锁码：并发注册同一个码时串行，后到者看到的已是 ACTIVE。激活码原样核对，不做规范化（契约）
        License license = licenseMapper.selectByHashForUpdate(keyedHasher.hash(activationCode));
        if (license == null) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        license.ensureClaimable();

        TeacherAccount account = insertTeacher(username);
        license.claimBy(account.getId(), clock.instant());
        if (licenseMapper.markClaimed(license.getId(), account.getId(), license.getActivatedAt()) != 1) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }

        TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        teacherAccountMapper.updateRefreshHash(account.getId(), tokens.refresh().hash());
        log.info("教师注册成功 userId={} username={} licenseId={}", account.getId(), username, license.getId());
        return TokenPairResponse.of(tokens, account);
    }

    /** @param username 已规范化为小写 */
    private TeacherAccount insertTeacher(String username) {
        if (teacherAccountMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        TeacherAccount account = new TeacherAccount();
        account.setUsername(username);
        account.setStatus(TeacherStatus.ENABLED);
        try {
            teacherAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 回读一次，拿到数据库生成的 created_at
        return teacherAccountMapper.selectById(account.getId());
    }
}
