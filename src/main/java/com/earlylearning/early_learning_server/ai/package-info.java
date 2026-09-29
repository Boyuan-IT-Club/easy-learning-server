/**
 * AI 处理模块（限界上下文）：录音转写与评分。组织规范见 {@code reference/adr/0008}。
 *
 * <p>模块 = 主包 {@code com.earlylearning.early_learning_server} 的直接子包；对外 API 只有根包的类型
 * （当前为空——别的模块不消费 AI）。模块内部统一四层，依赖单向
 * {@code interfaces → application → domain ← infrastructure}：
 *
 * <ul>
 *   <li>{@code interfaces} — Controller、请求/响应 DTO、请求形状校验；HTTP 是这一层的事。</li>
 *   <li>{@code application} — 应用服务：编排、幂等、任务提交；返回领域对象。</li>
 *   <li>{@code domain} — 任务状态机、评分记录与规则、评分依据、端口（ChatModel、SpeechTranscriber、
 *       StoryScorer、AnswerScorer、AiTaskStore）。</li>
 *   <li>{@code infrastructure} — 端口实现：ECNU 适配器、fake（缺省 provider）、字节级解析、任务内存登记。</li>
 * </ul>
 *
 * <p>方向由 {@code ArchitectureTests} 守护：模块间走根包 API（Spring Modulith verify），
 * 模块内 domain 不依赖外层。
 */
package com.earlylearning.early_learning_server.ai;
