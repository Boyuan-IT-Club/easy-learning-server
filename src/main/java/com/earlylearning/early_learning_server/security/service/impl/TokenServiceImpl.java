package com.earlylearning.early_learning_server.security.service.impl;

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
import com.earlylearning.early_learning_server.security.service.TokenService;

/** {@link TokenService} 的实现。 */
@Service
public class TokenServiceImpl implements TokenService {

    private final RedisTokenStore redisTokenStore;
    private final AuthProperties authProperties;
    private final Clock clock;

    public TokenServiceImpl(RedisTokenStore redisTokenStore, AuthProperties authProperties, Clock clock) {
        this.redisTokenStore = redisTokenStore;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    public TeacherTokens issueTeacher(int userId) {
        IssuedToken access = issue(TokenType.ACCESS, clock.instant().plus(authProperties.accessTokenTtl()));
        IssuedToken refresh = issue(TokenType.REFRESH, null);
        RedisTokenStore.TeacherGrant grant = new RedisTokenStore.TeacherGrant(userId, access.expiresAt());
        AfterCommit.run("save-teacher-access", () -> redisTokenStore.saveTeacher(access.hash(), grant,
                authProperties.accessTokenTtl().plus(authProperties.expiredRetention())));
        return new TeacherTokens(access, refresh);
    }

    @Override
    public IssuedToken issueAdmin(int adminId) {
        IssuedToken token = issue(TokenType.ADMIN, clock.instant().plus(authProperties.adminTokenTtl()));
        RedisTokenStore.AdminGrant grant = new RedisTokenStore.AdminGrant(adminId, token.expiresAt());
        AfterCommit.run("save-admin-token", () -> redisTokenStore.saveAdmin(token.hash(), grant,
                authProperties.adminTokenTtl().plus(authProperties.expiredRetention())));
        return token;
    }

    @Override
    public int requireTeacher(String token) {
        RedisTokenStore.TeacherGrant grant = redisTokenStore.findTeacher(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.userId();
    }

    @Override
    public int requireAdmin(String token) {
        RedisTokenStore.AdminGrant grant = redisTokenStore.findAdmin(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.adminId();
    }

    @Override
    public void revokeAdminAfterCommit(int adminId) {
        AfterCommit.run("revoke-admin", () -> redisTokenStore.deleteAllForAdmin(adminId));
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
