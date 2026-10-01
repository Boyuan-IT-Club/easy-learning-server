package com.earlylearning.early_learning_server.auth;

/** 已认证的身份。Controller 用 {@code @AuthenticationPrincipal AdminPrincipal} 等取得。 */
public sealed interface AuthPrincipal permits TeacherPrincipal, AdminPrincipal {

    /** Spring Security 的角色名（不含 ROLE_ 前缀）。 */
    String role();
}
