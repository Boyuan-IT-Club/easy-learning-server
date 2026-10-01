package com.earlylearning.early_learning_server.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首个管理员（契约："首次管理员由部署初始化"）。表里还没有任何管理员、且两项都配置了时才创建；
 * 已有管理员时忽略。密码只从环境变量读取，不写入仓库。
 */
@ConfigurationProperties(prefix = "admin.bootstrap")
public record AdminBootstrapProperties(String username, String password) {

    boolean configured() {
        return username != null && !username.isBlank() && password != null && !password.isEmpty();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[username=" + username + ", password=REDACTED]";
    }
}
