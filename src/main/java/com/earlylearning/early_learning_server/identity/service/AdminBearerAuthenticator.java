package com.earlylearning.early_learning_server.identity.service;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.identity.entity.AdminAccount;
import com.earlylearning.early_learning_server.security.model.AdminPrincipal;
import com.earlylearning.early_learning_server.security.model.AuthPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;
import com.earlylearning.early_learning_server.security.service.TokenService;

/** 认管理员 Token（{@code adt_}）。契约 AdminBearer：每个请求都校验账号仍为 ACTIVE。 */
@Component
public class AdminBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final AdminAccountService accounts;

    public AdminBearerAuthenticator(TokenService tokenService, AdminAccountService accounts) {
        this.tokenService = tokenService;
        this.accounts = accounts;
    }

    @Override
    public TokenType type() {
        return TokenType.ADMIN;
    }

    @Override
    public AuthPrincipal authenticate(String token) {
        int adminId = tokenService.requireAdmin(token);
        AdminAccount account = accounts.find(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return new AdminPrincipal(adminId);
    }
}
