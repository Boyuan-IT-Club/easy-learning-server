/**
 * 应用层：Token 签发与校验、教师注册与刷新、教师 Bearer 校验。服务返回领域对象，HTTP 形状是 interfaces 层的事。
 * 本包经 @NamedInterface 暴露为模块 API（admin 用 {@code TokenService} 签发与吊销管理员 Token）。
 */
@org.springframework.modulith.NamedInterface("application")
package com.earlylearning.early_learning_server.auth.application;
