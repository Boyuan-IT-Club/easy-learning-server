package com.earlylearning.early_learning_server.identity.service.impl;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.entity.AdminAccount;
import com.earlylearning.early_learning_server.identity.mapper.AdminAccountMapper;
import com.earlylearning.early_learning_server.security.model.AdminPrincipal;
import com.earlylearning.early_learning_server.security.model.AuthPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;
import com.earlylearning.early_learning_server.security.service.TokenService;

import lombok.RequiredArgsConstructor;

/** 认管理员 Token（{@code adt_}）。契约 AdminBearer：每个请求都校验账号仍为 ACTIVE。 */
@Component
@RequiredArgsConstructor
public class AdminBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final AdminAccountMapper adminAccountMapper;

    @Override
    public TokenType type() {
        return TokenType.ADMIN;
    }

    @Override
    public AuthPrincipal authenticate(String token) {
        int adminId = tokenService.requireAdmin(token);
        AdminAccount account = adminAccountMapper.selectById(adminId);
        if (account == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return new AdminPrincipal(adminId);
    }
}
