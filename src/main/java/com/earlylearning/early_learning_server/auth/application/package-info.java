/**
 * 应用层：Token 的签发、校验、吊销与刷新宽限。服务返回领域对象，HTTP 形状是 interfaces 层的事。
 * 本包经 @NamedInterface 暴露为模块 API（teacher、admin、license 调用 {@code TokenService}）。
 */
@org.springframework.modulith.NamedInterface("application")
package com.earlylearning.early_learning_server.auth.application;
