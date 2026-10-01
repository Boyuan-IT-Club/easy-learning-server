/**
 * 鉴权模块：Bearer 安全链与 Token 机制，以及教师注册、刷新与每个请求的教师账号校验。组织规范见 {@code reference/adr/0008}。
 *
 * <p>四层（依赖单向 {@code interfaces → application → domain ← infrastructure}）：
 * <ul>
 *   <li>interfaces：controller/（注册、刷新）、dto/、security/（安全链、Bearer 过滤器、错误写出）</li>
 *   <li>application：{@code TokenService}（签发、校验、吊销）、{@code RegistrationService}、{@code RefreshService}、
 *       {@code TeacherBearerAuthenticator}</li>
 *   <li>domain：Token 种类与签发结果、{@code BearerAuthenticator}（由本模块与 admin 模块各实现一个）、刷新宽限规则</li>
 *   <li>infrastructure：{@code RedisTokenStore}（access、管理员 Token、刷新宽限）、配置</li>
 * </ul>
 *
 * <p>依赖 teacher（建号、refresh 哈希）与 license（占码、查状态）；admin 依赖本模块签发管理员 Token。
 * 安全链只认 {@code BearerAuthenticator} 接口，不依赖 admin。教师密码与离线登录留在平板。
 */
package com.earlylearning.early_learning_server.auth;
