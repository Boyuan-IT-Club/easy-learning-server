/**
 * 已认证身份的类型：管理员与教师。由 auth 模块的安全链放进 SecurityContext，
 * Controller 用 {@code @AuthenticationPrincipal AdminPrincipal} 等取得。
 *
 * <p>放在 common 而不是 auth：各业务模块的 Controller 都要知道"当前是谁"，若依赖 auth，
 * 而 auth 又要调用这些模块（注册、刷新），模块之间就会成环。整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("security")
package com.earlylearning.early_learning_server.common.security;
