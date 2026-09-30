package com.earlylearning.early_learning_server.teacher.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.license.application.LicenseClaimService;
import com.earlylearning.early_learning_server.teacher.domain.RecoveryCode;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherSession;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 账号恢复：忘记本地密码、重装 App、换新设备时，凭管理员签发的恢复码重新拿到云端凭证（设计 D3）。
 *
 * <pre>
 *   账号的设备 = 本设备   → 同设备重置，不改绑定
 *   账号未绑定（已解绑）   → 绑定本设备
 *   账号绑在别的设备上     → DEVICE_ALREADY_BOUND，需管理员先解绑
 * </pre>
 *
 * <p>先不加锁地比对恢复码：比对失败要在独立事务里记错误次数（见 {@link RecoveryFailureRecorder}），
 * 若外层此时已锁住这行，独立事务会等外层释放锁，而外层又在等它返回——死锁。比对通过后再加锁复核。
 */
@Service
public class RecoveryService {

    private final TeacherAccountMapper mapper;
    private final LicenseClaimService licenseClaims;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final RecoveryFailureRecorder failureRecorder;
    private final KeyedHasher hasher;
    private final AuditLogService audit;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Clock clock;

    public RecoveryService(TeacherAccountMapper mapper,
                           LicenseClaimService licenseClaims,
                           TokenService tokenService,
                           SensitiveIdempotency sensitiveIdempotency,
                           RecoveryFailureRecorder failureRecorder,
                           KeyedHasher hasher,
                           AuditLogService audit,
                           SlidingWindowRateLimiter rateLimiter,
                           Clock clock) {
        this.mapper = mapper;
        this.licenseClaims = licenseClaims;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.failureRecorder = failureRecorder;
        this.hasher = hasher;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * @throws BusinessException 401 RECOVERY_CODE_INVALID（用户名不存在、码错误、过期、作废不区分）；
     *                           403 ACCOUNT_DISABLED / LICENSE_REVOKED；409 DEVICE_ALREADY_BOUND；429
     */
    public TeacherSession recover(String username, String rawCode, String deviceHeader, String idempotencyKey) {
        TeacherRequests.limitByIp(rateLimiter, TeacherRequests.RECOVER);
        DeviceId device = TeacherRequests.requireDevice(deviceHeader);
        // 格式不对也按"无效"回：不给探测者区分格式错与码错的机会
        String codeHash = RecoveryCode.parse(rawCode).map(code -> hasher.hash(code.value())).orElse(null);
        String fingerprint = InputFingerprint.of(username, codeHash, device.value());
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_RECOVER, idempotencyKey, fingerprint, 200,
                TeacherSession.class,
                () -> doRecover(username, codeHash, device),
                TeacherSession::redacted);
    }

    private TeacherSession doRecover(String username, String codeHash, DeviceId device) {
        Instant now = clock.instant();
        TeacherAccount candidate = username == null ? null : mapper.selectByUsername(username);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RECOVERY_CODE_INVALID);
        }
        if (codeHash == null || !candidate.recoveryCodeMatches(codeHash, now)) {
            failureRecorder.record(candidate.getId());
            throw new BusinessException(ErrorCode.RECOVERY_CODE_INVALID);
        }

        TeacherAccount account = mapper.selectForUpdate(candidate.getId());
        if (!account.recoveryCodeMatches(codeHash, now)) {
            // 比对与加锁之间，这枚码被并发的另一次恢复用掉了
            throw new BusinessException(ErrorCode.RECOVERY_CODE_INVALID);
        }
        account.checkStatus(licenseClaims.statusOfTeacher(account.getId())).ifPresent(code -> {
            throw new BusinessException(code);
        });

        boolean rebound;
        if (account.isBoundTo(Optional.of(device))) {
            rebound = false;
        } else if (!account.isDeviceBound()) {
            TeacherAccount other = mapper.selectByDeviceId(device.value());
            if (other != null) {
                throw new BusinessException(ErrorCode.DEVICE_ALREADY_BOUND);
            }
            account.bindDevice(device, now);
            rebound = true;
        } else {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_BOUND);
        }

        account.clearRecoveryCode();
        // 先登记吊销、再签发：两者都在提交后执行，按登记顺序进行，新签发的 access 不会被一并删掉
        tokenService.revokeTeacherAfterCommit(account.getId());
        TokenPair tokens = tokenService.issueTeacherPair(account.getId(), device.value());
        account.rotateRefresh(tokens.refresh().hash());
        mapper.updateBinding(account.getId(), account.getDeviceId(), account.getDeviceBoundAt(),
                account.getRefreshTokenHash());
        mapper.updateRecoveryCode(account.getId(), null, null, 0);
        audit.record(AuditEntry.byTeacher(account.getId(), AuditAction.TEACHER_RECOVERED)
                .withDetail(Map.of("device_rebound", rebound)));
        return new TeacherSession(account.getId(), account.getUsername(), rebound, tokens);
    }
}
