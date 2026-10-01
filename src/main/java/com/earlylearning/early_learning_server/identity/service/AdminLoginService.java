package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.identity.dto.AdminSessionResponse;

/**
 * 管理员登录（契约 adminLogin）。内部管理员，无公开注册；无刷新接口，过期重新登录。
 */
public interface AdminLoginService {

    /**
     * 校验用户名与密码，签发管理员 Token。
     *
     * <p>用户名不存在与密码错误返回同一个错误码 {@code INVALID_CREDENTIALS}；
     * 密码正确才会提示账号已停用（{@code ACCOUNT_DISABLED}）。
     *
     * @throws BusinessException 触发 IP 限流或用户名被锁定时 429 {@code RATE_LIMITED}
     */
    AdminSessionResponse login(String rawUsername, String password);
}
