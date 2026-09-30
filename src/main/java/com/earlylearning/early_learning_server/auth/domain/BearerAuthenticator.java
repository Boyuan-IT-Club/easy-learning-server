package com.earlylearning.early_learning_server.auth.domain;

/**
 * 端口：把一枚 Bearer Token 认成一个身份。
 *
 * <p>有两个真实实现：teacher 模块认 {@code at_}，admin 模块认 {@code adt_}。
 * 过滤器按 {@link #type()} 选择实现，因此 auth 模块不依赖任何业务模块。
 */
public interface BearerAuthenticator {

    /** 本实现负责的 Token 种类。 */
    TokenType type();

    /**
     * @param token          Authorization 头里的 Token 明文
     * @param deviceIdHeader {@code X-Device-Id} 头原文，可能为空
     * @return 已认证的身份
     * @throws com.earlylearning.early_learning_server.common.error.BusinessException
     *         认证失败：TOKEN_INVALID、TOKEN_EXPIRED、ACCOUNT_DISABLED、LICENSE_REVOKED、DEVICE_MISMATCH、
     *         DEPENDENCY_UNAVAILABLE 等，状态码与错误码直接写入响应
     */
    AuthPrincipal authenticate(String token, String deviceIdHeader);
}
