/**
 * 幂等键的占用与重放。
 *
 * <p>与业务无关：HTTP 写入接口用 {@code Idempotency-Key} 头，AI 提交接口用 {@code request_id}，
 * 两者共用这套机制，靠 {@link IdempotencyScope} 划分键空间。
 */
package com.earlylearning.early_learning_server.common.idempotency;
