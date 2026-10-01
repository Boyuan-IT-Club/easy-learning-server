/**
 * 不落库的业务对象与规则：任务状态机（{@code task}）、评分记录与运行时校验（{@code scoring}）、
 * 评分依据（{@code rubric}）、转写契约（{@code transcription}）、大模型接口（{@code llm}）。
 *
 * <p>外部能力的接口定义在这里，实现全部在 {@code client}。不依赖本模块的 controller / service / client 与 Spring Web。
 */
package com.earlylearning.early_learning_server.ai.model;
