package com.earlylearning.early_learning_server.auth.domain;

/** 教师身份：云端账号 id 与本次请求所在的设备。 */
public record TeacherPrincipal(int userId, String deviceId) implements AuthPrincipal {

    public static final String ROLE = "TEACHER";

    @Override
    public String role() {
        return ROLE;
    }
}
