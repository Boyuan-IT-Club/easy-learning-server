package com.earlylearning.early_learning_server.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.auth.domain.AdminAccessGrant;
import com.earlylearning.early_learning_server.auth.domain.IssuedToken;
import com.earlylearning.early_learning_server.auth.domain.TeacherAccessGrant;
import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.auth.infrastructure.RedisRefreshGraceRepository;
import com.earlylearning.early_learning_server.auth.infrastructure.RedisTokenRepository;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.common.tx.AfterCommit;

/**
 * Token 的签发、校验、吊销与刷新宽限。
 *
 * <p>本服务不认识教师、管理员的账号状态——那是 teacher、admin 模块的规则。它只回答
 * "这枚 Token 是不是我们发的、过没过期、属于谁"。
 *
 * <p>Redis 写入一律推迟到事务提交后（{@link AfterCommit}）：注册回滚了，就不能留下一枚可用的 Token。
 */
@Service
public class TokenService {

    private final RedisTokenRepository tokens;
    private final RedisRefreshGraceRepository grace;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(RedisTokenRepository tokens,
                        RedisRefreshGraceRepository grace,
                        AuthProperties properties,
                        Clock clock) {
        this.tokens = tokens;
        this.grace = grace;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 签发教师的一对凭证。
     *
     * <p>refresh_token 的哈希由调用方写进 user_account（契约 C5），本方法只负责 access 的存储。
     */
    public TokenPair issueTeacherPair(int userId, String deviceId) {
        Instant now = clock.instant();
        IssuedToken access = issue(TokenType.ACCESS, now.plus(properties.accessTokenTtl()));
        IssuedToken refresh = issue(TokenType.REFRESH, null);
        TeacherAccessGrant grant = new TeacherAccessGrant(userId, deviceId, access.expiresAt());
        AfterCommit.run("save-teacher-access", () -> tokens.saveTeacher(access.hash(), grant,
                properties.accessTokenTtl().plus(properties.expiredRetention())));
        return new TokenPair(access, refresh);
    }

    public IssuedToken issueAdmin(int adminId) {
        IssuedToken token = issue(TokenType.ADMIN, clock.instant().plus(properties.adminTokenTtl()));
        AdminAccessGrant grant = new AdminAccessGrant(adminId, token.expiresAt());
        AfterCommit.run("save-admin-token", () -> tokens.saveAdmin(token.hash(), grant,
                properties.adminTokenTtl().plus(properties.expiredRetention())));
        return token;
    }

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    public TeacherAccessGrant requireTeacher(String token) {
        TeacherAccessGrant grant = tokens.findTeacher(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant;
    }

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    public AdminAccessGrant requireAdmin(String token) {
        AdminAccessGrant grant = tokens.findAdmin(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant;
    }

    /** 事务提交后吊销该教师名下全部 access_token（停用、解绑、撤销激活码、恢复时调用）。 */
    public void revokeTeacherAfterCommit(int userId) {
        AfterCommit.run("revoke-teacher", () -> tokens.deleteAllForTeacher(userId));
    }

    /** @param keepTokenHash 保留的那一枚，例如改密码时当前会话；为 null 时全部吊销 */
    public void revokeAdminAfterCommit(int adminId, String keepTokenHash) {
        AfterCommit.run("revoke-admin", () -> tokens.deleteAllForAdmin(adminId, keepTokenHash));
    }

    public void revokeAdminToken(int adminId, String tokenHash) {
        tokens.deleteAdmin(adminId, tokenHash);
    }

    /** 刷新宽限：旧 refresh 哈希 → 本次轮换得到的新凭证。提交后写入。 */
    public void rememberRotation(String oldRefreshHash, TokenPair rotated) {
        AfterCommit.run("remember-rotation", () -> grace.remember(oldRefreshHash, rotated, properties.refreshGrace()));
    }

    public Optional<TokenPair> findRotation(String oldRefreshHash) {
        return grace.find(oldRefreshHash);
    }

    private IssuedToken issue(TokenType type, Instant expiresAt) {
        String value = Tokens.generate(type.prefix());
        return new IssuedToken(value, Tokens.sha256Hex(value), expiresAt);
    }

    private void ensureNotExpired(Instant expiresAt) {
        if (!clock.instant().isBefore(expiresAt)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
    }
}
