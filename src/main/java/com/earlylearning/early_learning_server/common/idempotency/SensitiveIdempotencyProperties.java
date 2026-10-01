package com.earlylearning.early_learning_server.common.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param sensitiveReplayTtl 含凭证的完整结果在 Redis 中的保留时长 */
@ConfigurationProperties(prefix = "idempotency")
public record SensitiveIdempotencyProperties(@DefaultValue("10m") Duration sensitiveReplayTtl) {
}
