package com.earlylearning.early_learning_server.teacher.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 恢复码规则。
 *
 * @param recoveryCodeTtl         恢复码有效期
 * @param recoveryMaxFailures     同一枚恢复码允许的累计错误次数，达到即作废
 */
@ConfigurationProperties(prefix = "teacher")
public record TeacherProperties(@DefaultValue("24h") Duration recoveryCodeTtl,
                                @DefaultValue("5") int recoveryMaxFailures) {
}
