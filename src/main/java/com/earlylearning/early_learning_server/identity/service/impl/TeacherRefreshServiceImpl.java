package com.earlylearning.early_learning_server.identity.service.impl;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.entity.TeacherAccount;
import com.earlylearning.early_learning_server.identity.client.RefreshGraceStore;
import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.identity.model.RefreshGrace;
import com.earlylearning.early_learning_server.identity.service.TeacherRefreshService;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;
import com.earlylearning.early_learning_server.security.service.TokenService;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link TeacherRefreshService} 的实现。
 *
 * <pre>
 *   当前凭证：锁账号 → 校验账号与激活码 → 签发新的一对 → 条件替换 refresh_token_hash → 记录 30 秒宽限
 *   上一枚凭证：宽限记录存在且仍对应数据库当前值 → 校验账号与激活码 → 返回同一组新凭证
 *   其他：401 REFRESH_TOKEN_INVALID
 * </pre>
 *
 * <p>"新凭证又轮换一次后，更早的凭证立即失效"由 {@link RefreshGrace#stillCurrent} 保证；
 * 宽限按旧哈希存、到期即删，所以最多保留紧邻当前的一枚旧凭证。
 *
 * <p>宽限记录在轮换事务<b>提交前</b>写入：并发的第二个刷新请求在行锁上等到第一个提交时，记录必须已经存在，
 * 否则它会在"已提交、尚未写缓存"的空档里拿到 401。提前写入是安全的：事务回滚后这条记录永远对不上。
 */
@Service
public class TeacherRefreshServiceImpl implements TeacherRefreshService {

    private final TeacherAccountMapper teachers;
    private final LicenseMapper licenses;
    private final TokenService tokenService;
    private final RefreshGraceStore graces;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final ObjectMapper objectMapper;

    public TeacherRefreshServiceImpl(TeacherAccountMapper teachers,
                                     LicenseMapper licenses,
                                     TokenService tokenService,
                                     RefreshGraceStore graces,
                                     SensitiveIdempotency sensitiveIdempotency,
                                     ObjectMapper objectMapper) {
        this.teachers = teachers;
        this.licenses = licenses;
        this.tokenService = tokenService;
        this.graces = graces;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.objectMapper = objectMapper;
    }

    @Override
    public TokenPairResponse refresh(String refreshToken, String idempotencyKey) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/refresh_token"));
        }
        String hash = Tokens.sha256Hex(refreshToken);
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REFRESH, idempotencyKey, hash, 200,
                TokenPairResponse.class, () -> rotateOrReuse(hash),
                UserAccountResponse.class, TokenPairResponse::user,
                snapshot -> null, this::guardReplay);
    }

    private TokenPairResponse rotateOrReuse(String hash) {
        TeacherAccount current = teachers.selectByRefreshHashForUpdate(hash);
        return current != null ? rotate(current, hash) : reuseWithinGrace(hash);
    }

    private TokenPairResponse rotate(TeacherAccount account, String oldHash) {
        account.ensureCloudAccess(licenses.selectByUserId(account.getId()));
        TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        if (teachers.rotateRefresh(account.getId(), oldHash, tokens.refresh().hash()) != 1) {
            // 已持有行锁，正常不会走到这里；万一走到，按凭证失效处理，不返回未提交的结果
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TokenPairResponse pair = TokenPairResponse.of(tokens, account);
        graces.remember(oldHash, new RefreshGrace(account.getId(), tokens.refresh().hash(),
                objectMapper.writeValueAsString(pair)));
        return pair;
    }

    private TokenPairResponse reuseWithinGrace(String oldHash) {
        RefreshGrace grace = graces.find(oldHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        // 锁住账号再比对：与正在进行的轮换串行
        TeacherAccount account = teachers.selectForUpdate(grace.userId());
        if (account == null || !grace.stillCurrent(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        account.ensureCloudAccess(licenses.selectByUserId(account.getId()));
        return objectMapper.readValue(grace.pairJson(), TokenPairResponse.class);
    }

    /**
     * 同键重放前再校验一次：账号停用或激活码撤销 → 403；
     * 重放里的 refresh 已不是当前凭证（之后又轮换过）→ 结果不能再用，按敏感结果过期处理。
     */
    private void guardReplay(TokenPairResponse replayed) {
        TeacherAccount account = teachers.selectById(replayed.user().id());
        if (account == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        account.ensureCloudAccess(licenses.selectByUserId(account.getId()));
        if (!Tokens.sha256Hex(replayed.refreshToken()).equals(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.SENSITIVE_RESULT_EXPIRED);
        }
    }
}
