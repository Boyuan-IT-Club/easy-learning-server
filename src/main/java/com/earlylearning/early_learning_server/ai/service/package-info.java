/**
 * 业务编排：提交任务（幂等 / 重试由 model 的 {@code AiTaskSubmission} 承担）、解析图片、调用评分能力。
 * 返回业务对象，HTTP 形状由 controller 转换。
 */
package com.earlylearning.early_learning_server.ai.service;
