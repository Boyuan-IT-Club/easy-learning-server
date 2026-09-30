/**
 * 管理员模块（限界上下文）：管理员账号、登录与会话、初始管理员。组织规范见 {@code reference/adr/0008}。
 *
 * <p>数据边界：admin_account。管理员没有任何读取儿童业务数据的入口（PRD 1.2、2.1）。
 * 本模块不对外暴露 API：它通过实现 auth 的 {@code BearerAuthenticator} 接入安全链。
 * 依据：01_账号与鉴权 v0.2。
 */
package com.earlylearning.early_learning_server.admin;
