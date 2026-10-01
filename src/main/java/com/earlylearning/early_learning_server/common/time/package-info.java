/**
 * 统一时钟：业务里的"现在"都从这里取，测试可替换为固定时钟来验证有效期、限流窗口等。
 *
 * <p>整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("time")
package com.earlylearning.early_learning_server.common.time;
