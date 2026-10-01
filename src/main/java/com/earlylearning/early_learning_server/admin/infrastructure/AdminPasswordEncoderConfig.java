package com.earlylearning.early_learning_server.admin.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/**
 * 管理员密码哈希用 PBKDF2，而不是 BCrypt：BCrypt 只取前 72 字节，128 个字符的密码会被静默截断。
 * 编码器来自 spring-security-crypto，不新增依赖。
 */
@Configuration(proxyBeanMethods = false)
public class AdminPasswordEncoderConfig {

    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
