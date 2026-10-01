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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** {@link TokenService} 的实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenServiceImpl implements TokenService {

    private final RedisTokenStore redisTokenStore;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Override
    public TeacherTokens issueTeacher(int userId) {
        IssuedToken access = issue(TokenType.ACCESS, clock.instant().plus(authProperties.accessTokenTtl()));
        IssuedToken refresh = issue(TokenType.REFRESH, null);
        RedisTokenStore.TeacherGrant grant = new RedisTokenStore.TeacherGrant(userId, access.expiresAt());
        AfterCommit.run("save-teacher-access", () -> redisTokenStore.saveTeacher(access.hash(), grant,
                authProperties.accessTokenTtl().plus(authProperties.expiredRetention())));
        log.debug("签发教师凭证 userId={} accessExpiresAt={}", userId, access.expiresAt());
        return new TeacherTokens(access, refresh);
    }

    @Override
    public IssuedToken issueAdmin(int adminId) {
        IssuedToken token = issue(TokenType.ADMIN, clock.instant().plus(authProperties.adminTokenTtl()));
        RedisTokenStore.AdminGrant grant = new RedisTokenStore.AdminGrant(adminId, token.expiresAt());
        AfterCommit.run("save-admin-token", () -> redisTokenStore.saveAdmin(token.hash(), grant,
                authProperties.adminTokenTtl().plus(authProperties.expiredRetention())));
        log.debug("签发管理员 Token adminId={} expiresAt={}", adminId, token.expiresAt());
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
        AfterCommit.run("revoke-admin", () -> {
            redisTokenStore.deleteAllForAdmin(adminId);
            log.info("已吊销管理员的全部 Token adminId={}", adminId);
        });
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
