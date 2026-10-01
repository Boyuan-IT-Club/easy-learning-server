package com.earlylearning.early_learning_server.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.common.tx.AfterCommit;

/**
 * Token 的签发、校验与吊销。只回答"这枚 Token 是不是我们发的、过没过期、属于谁"；
 * 账号能不能用由各认证实现查库判断。
 *
 * <p>Redis 写入一律推迟到事务提交后：注册回滚了，就不能留下一枚可用的 Token。
 */
@Service
public class TokenService {

    private final RedisTokenStore store;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(RedisTokenStore store, AuthProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** 教师的一对凭证。refresh 哈希由调用方写入 user_account（契约：数据库仅保存当前 refresh_token_hash）。 */
    public TeacherTokens issueTeacher(int userId) {
        IssuedToken access = issue(TokenType.ACCESS, clock.instant().plus(properties.accessTokenTtl()));
        IssuedToken refresh = issue(TokenType.REFRESH, null);
        RedisTokenStore.TeacherGrant grant = new RedisTokenStore.TeacherGrant(userId, access.expiresAt());
        AfterCommit.run("save-teacher-access", () -> store.saveTeacher(access.hash(), grant,
                properties.accessTokenTtl().plus(properties.expiredRetention())));
        return new TeacherTokens(access, refresh);
    }

    public IssuedToken issueAdmin(int adminId) {
        IssuedToken token = issue(TokenType.ADMIN, clock.instant().plus(properties.adminTokenTtl()));
        RedisTokenStore.AdminGrant grant = new RedisTokenStore.AdminGrant(adminId, token.expiresAt());
        AfterCommit.run("save-admin-token", () -> store.saveAdmin(token.hash(), grant,
                properties.adminTokenTtl().plus(properties.expiredRetention())));
        return token;
    }

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    public int requireTeacher(String token) {
        RedisTokenStore.TeacherGrant grant = store.findTeacher(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.userId();
    }

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    public int requireAdmin(String token) {
        RedisTokenStore.AdminGrant grant = store.findAdmin(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.adminId();
    }

    /** 改密码或停用后，该管理员已签发的 Token 全部失效（契约 updateAdminAccount）。提交后执行。 */
    public void revokeAdminAfterCommit(int adminId) {
        AfterCommit.run("revoke-admin", () -> store.deleteAllForAdmin(adminId));
    }

    /**
     * 宽限：保留 30 秒（契约 refreshTeacherToken）。
     *
     * <p>在轮换事务<b>提交前</b>写入：并发的第二个刷新请求在行锁上等到第一个提交时，宽限记录必须已经存在，
     * 否则它会在"已提交、尚未写缓存"的空档里拿到 401。提前写入是安全的——宽限只在
     * "记录里的新哈希 = 数据库当前值"时生效，事务回滚后这条记录永远对不上。
     */
    public void rememberGrace(String oldRefreshHash, RedisTokenStore.Grace grace) {
        store.rememberGrace(oldRefreshHash, grace, properties.refreshGrace());
    }

    public Optional<RedisTokenStore.Grace> findGrace(String oldRefreshHash) {
        return store.findGrace(oldRefreshHash);
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

    /** 教师的 access 与 refresh。 */
    public record TeacherTokens(IssuedToken access, IssuedToken refresh) {
    }
}
