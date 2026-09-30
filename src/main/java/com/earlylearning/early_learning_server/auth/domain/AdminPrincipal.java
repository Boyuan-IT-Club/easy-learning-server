package com.earlylearning.early_learning_server.auth.domain;

/**
 * 管理员身份。
 *
 * @param tokenHash 本次请求所用 Token 的哈希；退出、改密码时用它区分"当前这一枚"
 */
public record AdminPrincipal(int adminId, String tokenHash) implements AuthPrincipal {

    public static final String ROLE = "ADMIN";

    @Override
    public String role() {
        return ROLE;
    }

    @Override
    public String toString() {
        return "AdminPrincipal[adminId=" + adminId + "]";
    }
}
