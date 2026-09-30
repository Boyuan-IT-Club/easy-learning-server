package com.earlylearning.early_learning_server.admin.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 管理员密码编码：Spring 的委托编码器，默认 BCrypt，哈希带 {@code {bcrypt}} 前缀，以后换算法不用迁移旧数据。
 *
 * <p>教师密码不在这里：云端不保存教师密码，教师的离线密码只在平板上。
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
