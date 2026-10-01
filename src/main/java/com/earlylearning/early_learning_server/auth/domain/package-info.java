/**
 * 领域层：Token 种类与签发结果、注册 / 刷新结果、刷新宽限规则，以及 {@code BearerAuthenticator}
 * （两个真实实现：本模块认教师 access，admin 模块认管理员 Token）。本包经 @NamedInterface 暴露为模块 API。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.earlylearning.early_learning_server.auth.domain;
