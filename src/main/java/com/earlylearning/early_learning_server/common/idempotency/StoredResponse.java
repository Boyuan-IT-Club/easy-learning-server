package com.earlylearning.early_learning_server.common.idempotency;

/**
 * 首次执行的结果快照。
 *
 * <p>{@code body} 是调用方领域对象的 JSON 原文（不假定它是 HTTP 响应形状）；
 * 重放方按首次相同的映射方式把它还原成结果，因此两条路径的表现一致。
 */
public record StoredResponse(int httpStatus, String body) {
}
