package com.earlylearning.early_learning_server.admin.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始管理员。Flyway 只建表不建管理员，第一个管理员由这里创建。
 *
 * <p>只在 admin_account 为空时生效；表里已有管理员时这两项被忽略。首次启动后应从环境变量中删掉密码。
 */
@ConfigurationProperties(prefix = "admin.bootstrap")
public record AdminBootstrapProperties(String username, String password) {

    @Override
    public String toString() {
        return "AdminBootstrapProperties[username=" + username + ", password=REDACTED]";
    }
}
