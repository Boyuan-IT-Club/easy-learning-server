package com.earlylearning.early_learning_server.security.model;

/** 管理员身份。 */
public record AdminPrincipal(int adminId) implements AuthPrincipal {

    public static final String ROLE = "ADMIN";

    @Override
    public String role() {
        return ROLE;
    }
}
