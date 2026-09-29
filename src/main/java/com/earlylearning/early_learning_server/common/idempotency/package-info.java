/**
 * 幂等键的占用与重放：快照是调用方领域对象，不假定 HTTP 形状。
 * 整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("idempotency")
package com.earlylearning.early_learning_server.common.idempotency;
