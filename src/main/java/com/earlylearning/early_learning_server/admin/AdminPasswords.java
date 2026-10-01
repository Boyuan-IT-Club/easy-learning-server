package com.earlylearning.early_learning_server.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 管理员密码规则（契约 {@code AdminPassword}）：8—128 字符，不裁剪空白，服务端只存哈希。
 *
 * <p>用 PBKDF2 而不是 BCrypt：BCrypt 只取前 72 字节，128 个字符的密码会被静默截断。
 * 编码器来自 spring-security-crypto，不新增依赖。
 */
@Configuration(proxyBeanMethods = false)
public class AdminPasswords {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** 按字符（码点）计长度；不 trim。 */
    static String require(String password, String fieldPath) {
        if (password == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
        return password;
    }
}
