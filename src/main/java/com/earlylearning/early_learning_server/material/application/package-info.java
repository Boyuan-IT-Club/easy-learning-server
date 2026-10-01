/**
 * 应用层：发布与查询用例的编排。服务返回领域对象，HTTP 形状是 interfaces 层的事。
 * 跨模块依赖只走对方暴露的命名接口：storage 的上传与查询服务、ai 的评分目录。
 */
package com.earlylearning.early_learning_server.material.application;
