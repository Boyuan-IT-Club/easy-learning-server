/**
 * 评分业务：故事叙述评分与单题（提示前/后）评分。
 *
 * <p>包含两条提交链路的服务、{@code AIScore v2} 与 {@code QuestionAIScore} 的模型、
 * 以及模型输出的**运行时语义校验**（维度集合、分数范围、证据必须是原文真实片段、图片分组覆盖）。
 *
 * <p>版本与条目在 {@code ai.rubric}；出站端口在 {@code ai.port}；默认假实现在 {@code ai.adapter.fake}。
 *
 * <p>关键约定：服务端**不合成"最终分"**——契约规定云端只评分与校验、结果由客户端写入本地；
 * 两次作答各自独立，服务端不建立它们之间的关联。
 *
 * <p>数据边界：评分材料只在调用期间处理，不落盘、不写日志。
 */
package com.earlylearning.early_learning_server.ai.score;
