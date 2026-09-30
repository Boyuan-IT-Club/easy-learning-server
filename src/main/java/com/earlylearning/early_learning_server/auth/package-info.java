/**
 * 鉴权模块（限界上下文）：Spring Security 衔接、不透明 Token 的签发与校验、刷新宽限。组织规范见 {@code reference/adr/0008}。
 *
 * <p>本模块<b>不认识</b>教师与管理员：它定义 {@code BearerAuthenticator} 端口，由 teacher、admin 模块各自实现，
 * 过滤器在运行时按 Token 前缀选择实现。这样依赖只有一个方向（teacher/admin → auth）。
 *
 * <p>数据边界：Token 只存哈希，放在 Redis（关闭持久化）；refresh_token 哈希在 teacher 模块的 user_account 表里。
 * application 与 domain 通过 @NamedInterface 对外暴露。
 * 依据：01_账号与鉴权 v0.2。
 */
package com.earlylearning.early_learning_server.auth;
