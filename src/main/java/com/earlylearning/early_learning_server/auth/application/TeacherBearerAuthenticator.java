package com.earlylearning.early_learning_server.auth.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.auth.domain.BearerAuthenticator;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.security.AuthPrincipal;
import com.earlylearning.early_learning_server.common.security.TeacherPrincipal;
import com.earlylearning.early_learning_server.license.application.LicenseBindingService;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.teacher.application.TeacherAccountService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;

/**
 * 认教师 access_token（{@code at_}）。契约 TeacherBearer：每个请求都校验教师 status = 1、绑定激活码为 ACTIVE。
 *
 * <p>不做状态缓存：停用立即生效；重新启用后，未过期的 access 立即恢复可用（契约原文）。
 */
@Component
public class TeacherBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final TeacherAccountService teachers;
    private final LicenseBindingService licenses;

    public TeacherBearerAuthenticator(TokenService tokenService,
                                      TeacherAccountService teachers,
                                      LicenseBindingService licenses) {
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
        TeacherAccount account = teachers.find(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        ensureUsable(account, licenses.statusOfUser(userId));
        return new TeacherPrincipal(userId);
    }

    /** 认证与刷新共用：停用 → 403 ACCOUNT_DISABLED；激活码不是 ACTIVE → 403 LICENSE_REVOKED。 */
    static void ensureUsable(TeacherAccount account, Optional<LicenseStatus> license) {
        if (!account.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (license.isEmpty() || license.get() != LicenseStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LICENSE_REVOKED);
        }
    }
}
