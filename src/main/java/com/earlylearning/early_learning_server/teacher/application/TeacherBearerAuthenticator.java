package com.earlylearning.early_learning_server.teacher.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.AuthPrincipal;
import com.earlylearning.early_learning_server.auth.domain.BearerAuthenticator;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.auth.domain.TeacherAccessGrant;
import com.earlylearning.early_learning_server.auth.domain.TeacherPrincipal;
import com.earlylearning.early_learning_server.auth.domain.TokenType;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.license.application.LicenseClaimService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 认教师 Token（{@code at_}）。每个请求都查库（契约 TeacherBearer），不做状态缓存：停用、解绑立即生效。
 *
 * <p>设备要三方一致：Token 签发时的设备 = 请求头里的设备 = 账号当前绑定的设备。
 * 解绑后重新绑定到新设备时，旧设备手里没过期的 access 也会因此失效。
 */
@Component
public class TeacherBearerAuthenticator implements BearerAuthenticator {

    private final TokenService tokenService;
    private final TeacherAccountMapper mapper;
    private final LicenseClaimService licenseClaims;

    public TeacherBearerAuthenticator(TokenService tokenService,
                                      TeacherAccountMapper mapper,
                                      LicenseClaimService licenseClaims) {
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.licenseClaims = licenseClaims;
    }

    @Override
    public TokenType type() {
        return TokenType.ACCESS;
    }

    @Override
    public AuthPrincipal authenticate(String token, String deviceIdHeader) {
        TeacherAccessGrant grant = tokenService.requireTeacher(token);
        TeacherAccount account = mapper.selectById(grant.userId());
        if (account == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Optional<DeviceId> device = DeviceId.parse(deviceIdHeader)
                .filter(requested -> requested.value().equals(grant.deviceId()));
        account.checkUsable(device, licenseClaims.statusOfTeacher(account.getId())).ifPresent(code -> {
            throw new BusinessException(code);
        });
        return new TeacherPrincipal(account.getId(), grant.deviceId());
    }
}
