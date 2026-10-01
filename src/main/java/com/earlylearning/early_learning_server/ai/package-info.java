/**
 * AI 处理模块：录音转写与评分。代码组织见 AGENTS.md 第 3 节。
 *
 * <pre>
 *   controller/  入口：转写、故事评分、单题评分、任务查询、评分目录（validation/ 放请求形状校验）
 *   dto/         请求与响应
 *   service/     用例编排：提交任务、解析图片、调用评分能力
 *   model/       不落库的业务对象与规则：任务状态机（task）、评分记录与校验（scoring）、评分依据（rubric）、
 *                转写契约（transcription），以及外部能力的接口（ChatModel、SpeechTranscriber、StoryScorer…）
 *   client/      外部能力的实现：ECNU 大模型、fake 缺省实现、音频解析、任务内存登记
 * </pre>
 *
 * <p>本模块没有数据表。对外只暴露 {@code service.rubric}（评估材料发布时校验评分目录）。
 */
package com.earlylearning.early_learning_server.ai;
