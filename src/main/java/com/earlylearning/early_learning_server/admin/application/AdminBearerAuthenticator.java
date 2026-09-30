package com.earlylearning.early_learning_server.admin.application;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.admin.infrastructure.AdminAccountMapper;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.AdminAccessGrant;
import com.earlylearning.early_learning_server.auth.domain.AdminPrincipal;
import com.earlylearning.early_learning_server.auth.domain.AuthPrincipal;
import com.earlylearning.early_learning_server.auth.domain.BearerAuthenticator;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.Tokens;

/**
 * 认管理员 Token（{@code adt_}）：Token 有效，且管理员仍为 ACTIVE（契约 AdminBearer：每个请求都校验）。
 */
@Component
public class AdminBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final AdminAccountMapper mapper;

    public AdminBearerAuthenticator(TokenService tokenService, AdminAccountMapper mapper) {
        this.tokenService = tokenService;
        this.mapper = mapper;
    }

    @Override
    public TokenType type() {
        return TokenType.ADMIN;
    }

    @Override
    public AuthPrincipal authenticate(String token, String deviceIdHeader) {
        AdminAccessGrant grant = tokenService.requireAdmin(token);
        AdminAccount account = mapper.selectById(grant.adminId());
        if (account == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        account.ensureActive();
        return new AdminPrincipal(account.getId(), Tokens.sha256Hex(token));
    }
}
