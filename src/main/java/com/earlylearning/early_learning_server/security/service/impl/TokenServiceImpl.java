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

    private final RedisTokenStore store;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenServiceImpl(RedisTokenStore store, AuthProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public TeacherTokens issueTeacher(int userId) {
        IssuedToken access = issue(TokenType.ACCESS, clock.instant().plus(properties.accessTokenTtl()));
        IssuedToken refresh = issue(TokenType.REFRESH, null);
        RedisTokenStore.TeacherGrant grant = new RedisTokenStore.TeacherGrant(userId, access.expiresAt());
        AfterCommit.run("save-teacher-access", () -> store.saveTeacher(access.hash(), grant,
                properties.accessTokenTtl().plus(properties.expiredRetention())));
        return new TeacherTokens(access, refresh);
    }

    @Override
    public IssuedToken issueAdmin(int adminId) {
        IssuedToken token = issue(TokenType.ADMIN, clock.instant().plus(properties.adminTokenTtl()));
        RedisTokenStore.AdminGrant grant = new RedisTokenStore.AdminGrant(adminId, token.expiresAt());
        AfterCommit.run("save-admin-token", () -> store.saveAdmin(token.hash(), grant,
                properties.adminTokenTtl().plus(properties.expiredRetention())));
        return token;
    }

    @Override
    public int requireTeacher(String token) {
        RedisTokenStore.TeacherGrant grant = store.findTeacher(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.userId();
    }

    @Override
    public int requireAdmin(String token) {
        RedisTokenStore.AdminGrant grant = store.findAdmin(Tokens.sha256Hex(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        ensureNotExpired(grant.expiresAt());
        return grant.adminId();
    }

    @Override
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
