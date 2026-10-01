/**
 * 应用层：用例编排。提交任务（幂等/重试由 domain 的 {@code AiTaskSubmission} 承担）、解析图片、
 * 调用端口、返回领域对象——不返回 DTO，HTTP 形状是 interfaces 层的事。
 */
package com.earlylearning.early_learning_server.ai.application;
