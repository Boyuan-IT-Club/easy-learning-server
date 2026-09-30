package com.earlylearning.early_learning_server.teacher.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TeacherPrincipal;
import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.license.application.LicenseClaimService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 刷新凭证与"我是谁"。
 *
 * <p>刷新按契约 v1.6.0：refresh_token 不过期，只在成功时轮换；旧凭证 30 秒内重试返回同一组新凭证。
 * 账号被停用时不轮换、也不清空 refresh 哈希——管理员重新启用后，平板用原来的凭证就能恢复。
 */
@Service
public class TeacherSessionService {

    private final TeacherAccountMapper mapper;
    private final LicenseClaimService licenseClaims;
    private final TokenService tokenService;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Clock clock;

    public TeacherSessionService(TeacherAccountMapper mapper,
                                 LicenseClaimService licenseClaims,
                                 TokenService tokenService,
                                 SlidingWindowRateLimiter rateLimiter,
                                 Clock clock) {
        this.mapper = mapper;
        this.licenseClaims = licenseClaims;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * @throws BusinessException 401 REFRESH_TOKEN_INVALID；403 ACCOUNT_DISABLED / LICENSE_REVOKED / DEVICE_MISMATCH；429
     */
    @Transactional
    public TokenPair refresh(String refreshToken, String deviceHeader) {
        DeviceId device = TeacherRequests.requireDevice(deviceHeader);
        if (!rateLimiter.tryAcquire(TeacherRequests.REFRESH, device.value())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        if (refreshToken == null || TokenType.of(refreshToken).orElse(null) != TokenType.REFRESH) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String oldHash = Tokens.sha256Hex(refreshToken);

        Optional<TokenPair> rotated = tokenService.findRotation(oldHash);
        if (rotated.isPresent()) {
            return rotated.get();
        }
        TeacherAccount account = mapper.selectByRefreshHash(oldHash);
        if (account == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        account.checkUsable(Optional.of(device), licenseClaims.statusOfTeacher(account.getId()))
                .ifPresent(code -> {
                    throw new BusinessException(code);
                });

        TokenPair pair = tokenService.issueTeacherPair(account.getId(), device.value());
        if (mapper.rotateRefresh(account.getId(), oldHash, pair.refresh().hash(), clock.instant()) != 1) {
            // 并发的另一个刷新先轮换了：它提交后宽限缓存里就有结果
            return tokenService.findRotation(oldHash)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        }
        tokenService.rememberRotation(oldHash, pair);
        return pair;
    }

    /** 能走到这里说明安全链已校验过账号可用，所以状态恒为 ACTIVE。 */
    public TeacherAccount me(TeacherPrincipal principal) {
        TeacherAccount account = mapper.selectById(principal.userId());
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return account;
    }
}
