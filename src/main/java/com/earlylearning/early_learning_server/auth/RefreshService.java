package com.earlylearning.early_learning_server.auth;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.auth.web.TokenPairResponse;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.license.LicenseLookup;
import com.earlylearning.early_learning_server.license.LicenseStatus;
import com.earlylearning.early_learning_server.teacher.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.TeacherAccountService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 教师刷新凭证（契约 refreshTeacherToken）。
 *
 * <pre>
 *   外层：幂等键，同键同输入重放完整结果；缓存丢失 → 409 SENSITIVE_RESULT_EXPIRED
 *   当前凭证：锁账号 → 校验账号与激活码 → 签发新的一对 → 条件替换 refresh_token_hash → 记录 30 秒宽限
 *   上一枚凭证：宽限记录存在且"记录里的新哈希 = 数据库当前值" → 校验账号与激活码 → 返回同一组新凭证
 *   其他：401 REFRESH_TOKEN_INVALID
 * </pre>
 *
 * <p>"新凭证又轮换一次后，更早的凭证立即失效"由"新哈希 = 数据库当前值"保证；
 * 宽限按旧哈希存、到期即删，所以最多保留紧邻当前的一枚旧凭证。
 */
@Service
public class RefreshService {

    private final TeacherAccountService teachers;
    private final LicenseLookup licenses;
    private final TokenService tokenService;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final ObjectMapper objectMapper;

    public RefreshService(TeacherAccountService teachers,
                          LicenseLookup licenses,
                          TokenService tokenService,
                          SensitiveIdempotency sensitiveIdempotency,
                          ObjectMapper objectMapper) {
        this.teachers = teachers;
        this.licenses = licenses;
        this.tokenService = tokenService;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.objectMapper = objectMapper;
    }

    public StoredResponse refresh(String refreshToken, String idempotencyKey) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/refresh_token"));
        }
        String hash = Tokens.sha256Hex(refreshToken);
        return sensitiveIdempotency.execute(IdempotencyScope.AUTH_REFRESH, idempotencyKey, hash, 200,
                () -> rotateOrReuse(hash),
                ApiResponse::ok,
                pair -> ApiResponse.ok(Map.of("user", pair.user())),
                snapshot -> null,
                this::guardReplay);
    }

    private TokenPairResponse rotateOrReuse(String hash) {
        TeacherAccount current = teachers.lockByRefreshHash(hash);
        if (current != null) {
            return rotate(current, hash);
        }
        return reuseWithinGrace(hash);
    }

    private TokenPairResponse rotate(TeacherAccount account, String oldHash) {
        ensureUsable(account);
        TokenService.TeacherTokens tokens = tokenService.issueTeacher(account.getId());
        if (!teachers.rotateRefresh(account.getId(), oldHash, tokens.refresh().hash())) {
            // 已持有行锁，正常不会走到这里；万一走到，按凭证失效处理，不返回未提交的结果
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TokenPairResponse pair = TokenPairResponse.of(tokens, account);
        tokenService.rememberGrace(oldHash, new RedisTokenStore.Grace(account.getId(), tokens.refresh().hash(),
                objectMapper.writeValueAsString(pair)));
        return pair;
    }

    private TokenPairResponse reuseWithinGrace(String oldHash) {
        RedisTokenStore.Grace grace = tokenService.findGrace(oldHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        // 锁住账号再比对：与正在进行的轮换串行
        TeacherAccount account = teachers.lockById(grace.userId());
        if (account == null || !grace.newRefreshHash().equals(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        ensureUsable(account);
        return objectMapper.readValue(grace.pair(), TokenPairResponse.class);
    }

    /**
     * 同键重放前再校验一次：账号停用或激活码撤销 → 403；
     * 重放里的 refresh 已不是当前凭证（之后又轮换过）→ 结果不能再用，按敏感结果过期处理。
     */
    private void guardReplay(String fullBody) {
        JsonNode data = objectMapper.readTree(fullBody).path("data");
        TeacherAccount account = teachers.find(data.path("user").path("id").asInt());
        if (account == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        ensureUsable(account);
        String replayedHash = Tokens.sha256Hex(data.path("refresh_token").asString());
        if (!replayedHash.equals(account.getRefreshTokenHash())) {
            throw new BusinessException(ErrorCode.SENSITIVE_RESULT_EXPIRED);
        }
    }

    private void ensureUsable(TeacherAccount account) {
        Optional<LicenseStatus> license = licenses.statusOfUser(account.getId());
        TeacherBearerAuthenticator.ensureUsable(account, license);
    }
}
