/**
 * AI 转写与评分。
 *
 * <p>包结构（按"变化的原因"分，不按技术种类）：
 * <ul>
 *   <li>{@code task}       —— 与业务无关的任务语义与执行基础设施；</li>
 *   <li>{@code transcribe} —— 转写业务；</li>
 *   <li>{@code score}      —— 故事评分与单题评分；{@code rubric} 是其版本与条目目录；</li>
 *   <li>{@code port}       —— 出站端口（会被替换的接口）；</li>
 *   <li>{@code adapter}    —— 端口实现，当前只有 {@code adapter.fake} 假实现；</li>
 *   <li>{@code web}        —— HTTP 边界：Controller、请求/响应 DTO、请求语义校验。</li>
 * </ul>
 * 依赖方向单向：{@code web → 业务 → task}、{@code adapter → port}，反向禁止。
 *
 * <p>任务状态**不落库**：结果只在内存有效期内可读，进程重启后客户端会收到 {@code PROCESS_RESTARTED}
 * 并重交材料。业务结果由移动端保存，服务端不长期持有儿童数据。
 *
 * <p>数据边界：无数据库表；儿童音频与回答文本不落盘、不写日志、不进备份。
 */
package com.earlylearning.early_learning_server.ai;
