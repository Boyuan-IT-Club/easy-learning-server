package com.earlylearning.early_learning_server.auth.application;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.auth.domain.RefreshGrace;
import com.earlylearning.early_learning_server.auth.domain.TeacherTokenPair;
import com.earlylearning.early_learning_server.auth.domain.TeacherTokens;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.license.application.LicenseBindingService;
import com.earlylearning.early_learning_server.teacher.application.TeacherAccountService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccountSummary;

/**
 * 教师刷新凭证（契约 refreshTeacherToken）。
 *
 * <pre>
 *   外层：幂等键，同键同输入重放完整结果；缓存丢失 → 409 SENSITIVE_RESULT_EXPIRED
 *   当前凭证：锁账号 → 校验账号与激活码 → 签发新的一对 → 条件替换 refresh_token_hash → 记录 30 秒宽限
 *   上一枚凭证：宽限记录存在且仍对应数据库当前值 → 校验账号与激活码 → 返回同一组新凭证
 *   其他：401 REFRESH_TOKEN_INVALID
 * </pre>
 *
 * <p>"新凭证又轮换一次后，更早的凭证立即失效"由 {@link RefreshGrace#stillCurrent} 保证；
 * 宽限按旧哈希存、到期即删，所以最多保留紧邻当前的一枚旧凭证。
 */
@Service
public class RefreshService {

    private final TeacherAccountService teachers;
    private final LicenseBindingService licenses;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;

    public RefreshService(TeacherAccountService teachers,
                          LicenseBindingService licenses,
                          TokenService tokenService,
                          SensitiveIdempotency sensitiveIdempotency) {
        this.teachers = teachers;
        this.licenses = licenses;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
    }

    public TeacherTokenPair refresh(String refreshToken, String idempotencyKey) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/refresh_token"));
        }
        String hash = Tokens.sha256Hex(refreshToken);
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REFRESH, idempotencyKey, hash, 200,
                TeacherTokenPair.class, () -> rotateOrReuse(hash),
                TeacherAccountSummary.class, TeacherTokenPair::user,
                snapshot -> null, this::guardReplay);
    }

    private TeacherTokenPair rotateOrReuse(String hash) {
        TeacherAccount current = teachers.lockByRefreshHash(hash);
        return current != null ? rotate(current, hash) : reuseWithinGrace(hash);
    }

    private TeacherTokenPair rotate(TeacherAccount account, String oldHash) {
        ensureUsable(account);
        TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        if (!teachers.rotateRefresh(account.getId(), oldHash, tokens.refresh().hash())) {
            // 已持有行锁，正常不会走到这里；万一走到，按凭证失效处理，不返回未提交的结果
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TeacherTokenPair pair = TeacherTokenPair.of(tokens, TeacherAccountSummary.of(account));
        tokenService.rememberGrace(oldHash, new RefreshGrace(account.getId(), tokens.refresh().hash(), pair));
        return pair;
    }

    private TeacherTokenPair reuseWithinGrace(String oldHash) {
        RefreshGrace grace = tokenService.findGrace(oldHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        // 锁住账号再比对：与正在进行的轮换串行
        TeacherAccount account = teachers.lockById(grace.userId());
        if (account == null || !grace.stillCurrent(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        ensureUsable(account);
        return grace.pair();
    }

    /**
     * 同键重放前再校验一次：账号停用或激活码撤销 → 403；
     * 重放里的 refresh 已不是当前凭证（之后又轮换过）→ 结果不能再用，按敏感结果过期处理。
     */
    private void guardReplay(TeacherTokenPair replayed) {
        TeacherAccount account = teachers.find(replayed.user().id());
        if (account == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        ensureUsable(account);
        if (!Tokens.sha256Hex(replayed.refreshToken()).equals(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.SENSITIVE_RESULT_EXPIRED);
        }
    }

    private void ensureUsable(TeacherAccount account) {
        TeacherBearerAuthenticator.ensureUsable(account, licenses.statusOfUser(account.getId()));
    }
}
