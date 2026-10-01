package com.earlylearning.early_learning_server.identity.service;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.entity.TeacherAccount;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.security.model.AuthPrincipal;
import com.earlylearning.early_learning_server.security.model.TeacherPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;
import com.earlylearning.early_learning_server.security.service.BearerAuthenticator;
import com.earlylearning.early_learning_server.security.service.TokenService;

/**
 * 认教师 access_token（{@code at_}）。契约 TeacherBearer：每个请求都校验教师 status = 1、绑定激活码为 ACTIVE。
 *
 * <p>不做状态缓存：停用立即生效；重新启用后，未过期的 access 立即恢复可用（契约原文）。
 */
@Component
public class TeacherBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final TeacherAccountMapper teachers;
    private final LicenseMapper licenses;

    public TeacherBearerAuthenticator(TokenService tokenService, TeacherAccountMapper teachers, LicenseMapper licenses) {
        this.tokenService = tokenService;
        this.teachers = teachers;
        this.licenses = licenses;
    }

    @Override
    public TokenType type() {
        return TokenType.ACCESS;
    }

    @Override
    public AuthPrincipal authenticate(String token) {
        int userId = tokenService.requireTeacher(token);
        TeacherAccount account = teachers.selectById(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        account.ensureCloudAccess(licenses.selectByUserId(userId));
        return new TeacherPrincipal(userId);
    }
}
