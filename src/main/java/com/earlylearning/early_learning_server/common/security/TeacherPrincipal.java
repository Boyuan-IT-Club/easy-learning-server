package com.earlylearning.early_learning_server.common.security;

/** 教师身份：云端账号 id。 */
public record TeacherPrincipal(int userId) implements AuthPrincipal {

    public static final String ROLE = "TEACHER";

    @Override
    public String role() {
        return ROLE;
    }
}
