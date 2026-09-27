/**
 * 日志与请求追踪。
 *
 * <p>traceId 由 {@code TraceIdFilter} 写入 MDC，logback 的 pattern 通过 {@code %X{traceId}} 读取。
 */
package com.earlylearning.early_learning_server.common.logging;
