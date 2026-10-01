/**
 * 鉴权机制模块：Bearer 安全链、Token 签发 / 校验 / 吊销、已认证身份类型。不含任何账号业务。
 *
 * <pre>
 *   config/   SecurityConfig（哪些路径免登录、哪些要哪种角色）、Token 有效期配置
 *   filter/   BearerTokenFilter：按前缀把 Token 交给对应的 BearerAuthenticator，写入 SecurityContext
 *   service/  TokenService（签发、校验、吊销）、BearerAuthenticator（由 identity 实现：教师、管理员各一个）
 *   model/    AuthPrincipal / AdminPrincipal / TeacherPrincipal、TokenType、IssuedToken、TeacherTokens
 *   client/   RedisTokenStore（Redis 须关闭持久化）
 * </pre>
 *
 * <p>只依赖 common。对外暴露 service 与 model：identity 用它签发 Token、实现认证；
 * 其他模块的 Controller 用 {@code @AuthenticationPrincipal TeacherPrincipal} 等取当前身份。
 */
package com.earlylearning.early_learning_server.security;
