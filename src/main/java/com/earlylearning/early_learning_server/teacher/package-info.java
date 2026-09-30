/**
 * 教师账号模块（限界上下文）：注册、刷新、恢复、后台管理，以及教师请求的认证。组织规范见 {@code reference/adr/0008}。
 *
 * <p>数据边界：user_account（教师云端账号）。云端不保存教师密码，离线登录与教师资料都在平板上（PRD 1.2、技术方案）。
 * 注册在同一事务里完成占码（license）、建号与绑定设备——这是唯一一处跨模块事务，PRD 2.2-3 要求原子完成。
 * 本模块不对外暴露 API：它通过实现 auth 的 {@code BearerAuthenticator} 接入安全链。
 * 依据：01_账号与鉴权 v0.2。
 */
package com.earlylearning.early_learning_server.teacher;
