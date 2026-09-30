package com.earlylearning.early_learning_server.teacher.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.license.application.LicenseClaimService;
import com.earlylearning.early_learning_server.license.domain.ActivationCode;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherSession;
import com.earlylearning.early_learning_server.teacher.domain.Username;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 激活码校验与注册。
 *
 * <p>注册在同一事务里完成：锁码 → 建号并绑定设备 → 占码 → 写 refresh 哈希 → 审计（PRD 2.2-3）。
 * 任何一步失败都整体回滚，激活码仍为 UNUSED。这是本项目里唯一一处同时改两个模块数据的事务。
 *
 * <p>云端不收密码：密码只在平板本地使用（C3、D6）。
 */
@Service
public class RegistrationService {

    private static final String USERNAME_UNIQUE = "uk_user_account_username";

    private final LicenseClaimService licenseClaims;
    private final TeacherAccountMapper mapper;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final AuditLogService audit;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Clock clock;

    public RegistrationService(LicenseClaimService licenseClaims,
                               TeacherAccountMapper mapper,
                               TokenService tokenService,
                               SensitiveIdempotency sensitiveIdempotency,
                               AuditLogService audit,
                               SlidingWindowRateLimiter rateLimiter,
                               Clock clock) {
        this.licenseClaims = licenseClaims;
        this.mapper = mapper;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 注册前的只读校验（PRD 2.2-2：先校验激活码，再开放注册）。
     *
     * @throws BusinessException 400 格式错；409 LICENSE_UNAVAILABLE（不存在、已用、已撤销不区分）；429
     */
    public void verifyLicense(String rawCode) {
        TeacherRequests.limitByIp(rateLimiter, TeacherRequests.LICENSE_VERIFY);
        ActivationCode code = parseCode(rawCode);
        if (!licenseClaims.isAvailable(code)) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
    }

    public TeacherSession register(String rawCode, String rawUsername, String deviceHeader, String idempotencyKey) {
        TeacherRequests.limitByIp(rateLimiter, TeacherRequests.REGISTER);
        ActivationCode code = parseCode(rawCode);
        Username username = Username.parse(rawUsername);
        DeviceId device = TeacherRequests.requireDevice(deviceHeader);
        String fingerprint = InputFingerprint.of(code.value(), username.value(), device.value());
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REGISTER, idempotencyKey, fingerprint, 201,
                TeacherSession.class,
                () -> createAccount(code, username, device),
                TeacherSession::redacted);
    }

    private TeacherSession createAccount(ActivationCode code, Username username, DeviceId device) {
        License license = licenseClaims.lockAvailable(code);
        if (mapper.selectByDeviceId(device.value()) != null) {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_BOUND);
        }
        if (mapper.selectByUsername(username.value()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        Instant now = clock.instant();
        TeacherAccount account = TeacherAccount.register(username, device, now);
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 预检之后的并发竞争：按撞上的唯一键区分原因
            throw new BusinessException(isUsernameConflict(e) ? ErrorCode.USERNAME_EXISTS
                    : ErrorCode.DEVICE_ALREADY_BOUND);
        }
        licenseClaims.claim(license, account.getId());
        TokenPair tokens = tokenService.issueTeacherPair(account.getId(), device.value());
        mapper.updateRefreshHash(account.getId(), tokens.refresh().hash());
        audit.record(AuditEntry.byTeacher(account.getId(), AuditAction.TEACHER_REGISTERED));
        return new TeacherSession(account.getId(), account.getUsername(), false, tokens);
    }

    private static ActivationCode parseCode(String rawCode) {
        return ActivationCode.parse(rawCode).orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/activation_code")));
    }

    private static boolean isUsernameConflict(DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(USERNAME_UNIQUE);
    }
}
