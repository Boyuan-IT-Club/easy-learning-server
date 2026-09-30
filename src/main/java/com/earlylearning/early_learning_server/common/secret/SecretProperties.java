package com.earlylearning.early_learning_server.common.secret;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 码哈希用的 pepper。
 *
 * <p>没有默认值：缺失时启动失败。上线后不得更换——激活码与恢复码都按 HMAC 值查库，
 * 换了 pepper，所有已发出的码都会查不到。
 */
@Validated
@ConfigurationProperties(prefix = "secret")
public record SecretProperties(@NotBlank @Size(min = 32) String codePepper) {

    @Override
    public String toString() {
        return "SecretProperties[codePepper=REDACTED]";
    }
}
