/**
 * 应用层：管理端用例与供其他模块使用的占码、查询能力。服务返回领域对象，HTTP 形状是 interfaces 层的事。
 * 本包经 @NamedInterface 暴露为模块 API（auth 注册时占码，teacher 启用前查激活码状态）。
 */
@org.springframework.modulith.NamedInterface("application")
package com.earlylearning.early_learning_server.license.application;
