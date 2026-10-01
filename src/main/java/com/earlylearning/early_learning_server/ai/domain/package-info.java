/**
 * 领域层：任务状态机（{@code task}）、评分记录与运行时校验（{@code scoring}）、评分依据（{@code rubric}）、
 * 转写契约（{@code transcription}）、大模型端口（{@code llm}）。
 *
 * <p>端口（接口）定义在这里，实现全部在 {@code infrastructure}。禁止依赖本模块的
 * interfaces / application / infrastructure 与 Spring Web。
 */
package com.earlylearning.early_learning_server.ai.domain;
