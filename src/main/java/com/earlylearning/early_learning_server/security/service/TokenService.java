package com.earlylearning.early_learning_server.security.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.common.tx.AfterCommit;
import com.earlylearning.early_learning_server.security.client.RedisTokenStore;
import com.earlylearning.early_learning_server.security.config.AuthProperties;
import com.earlylearning.early_learning_server.security.model.IssuedToken;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;
import com.earlylearning.early_learning_server.security.model.TokenType;

/**
 * Token 的签发、校验与吊销。只回答"这枚 Token 是不是我们发的、过没过期、属于谁"；
 * 账号能不能用由各 {@code BearerAuthenticator} 查库判断。
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
