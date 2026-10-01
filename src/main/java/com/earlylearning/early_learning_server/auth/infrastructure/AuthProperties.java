package com.earlylearning.early_learning_server.auth.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token 有效期。
 *
 * @param accessTokenTtl   教师 access_token 有效期（契约示例为 30 分钟）
 * @param adminTokenTtl    管理员 Token 有效期（契约示例为 8 小时，绝对过期，无刷新）
 * @param refreshGrace     refresh 轮换后上一枚凭证的宽限窗口（契约固定 30 秒）
 * @param expiredRetention 过期后仍能识别为"已过期"（回 TOKEN_EXPIRED）的时长，之后按"无效"处理
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@DefaultValue("30m") Duration accessTokenTtl,
                             @DefaultValue("8h") Duration adminTokenTtl,
                             @DefaultValue("30s") Duration refreshGrace,
                             @DefaultValue("24h") Duration expiredRetention) {
}
