package com.earlylearning.early_learning_server.common.idempotency;

/**
 * 首次执行的响应快照。
 *
 * <p>{@code body} 是契约形状的 JSON 原文，重试时逐字节原样返回，因此不会因序列化差异而变化。
 */
public record StoredResponse(int httpStatus, String body) {
}
