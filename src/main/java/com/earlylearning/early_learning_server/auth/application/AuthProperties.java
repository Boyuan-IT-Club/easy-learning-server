package com.earlylearning.early_learning_server.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token 有效期配置。
 *
 * @param accessTokenTtl   教师 access_token 有效期
 * @param adminTokenTtl    管理员 Token 有效期（绝对过期，无刷新）
 * @param refreshGrace     refresh 轮换后旧凭证的宽限窗口（契约 v1.6.0）
 * @param expiredRetention 过期后仍能识别为"已过期"的时长，之后按"无效"处理
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@DefaultValue("2h") Duration accessTokenTtl,
                             @DefaultValue("8h") Duration adminTokenTtl,
                             @DefaultValue("30s") Duration refreshGrace,
                             @DefaultValue("24h") Duration expiredRetention) {
}
