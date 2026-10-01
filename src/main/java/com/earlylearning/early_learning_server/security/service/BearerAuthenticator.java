package com.earlylearning.early_learning_server.security.service;

import com.earlylearning.early_learning_server.security.model.AuthPrincipal;
import com.earlylearning.early_learning_server.security.model.TokenType;

/**
 * 把一枚 Bearer Token 认成一个身份。两个实现在 identity：TeacherBearerAuthenticator 认教师的 {@code at_}，AdminBearerAuthenticator 认管理员的 {@code adt_}。
 * 过滤器按 {@link #type()} 选择实现。
 */
public interface BearerAuthenticator {

    TokenType type();

    /**
     * @throws com.earlylearning.early_learning_server.common.error.BusinessException
     *         TOKEN_INVALID、TOKEN_EXPIRED、ACCOUNT_DISABLED、LICENSE_REVOKED、DEPENDENCY_UNAVAILABLE 等，
     *         状态码与错误码直接写入响应
     */
    AuthPrincipal authenticate(String token);
}
