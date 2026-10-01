/**
 * 基于 Redis 的滑动窗口限流与失败锁定。
 *
 * <p>只提供机制；规则名、窗口与上限由调用方模块定义（见 {@link RateLimitRule}）。
 * Redis 故障时放行并记告警：限流是防护而不是正确性的一部分，不能因为它把登录整个堵死。
 */
package com.earlylearning.early_learning_server.common.ratelimit;
