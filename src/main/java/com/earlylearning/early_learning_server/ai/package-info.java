/**
 * AI 转写与评分。
 *
 * <p>包结构（按"变化的原因"分，不按技术种类）：
 * <ul>
 *   <li>{@code task}       —— 与业务无关的任务语义与执行基础设施；</li>
 *   <li>{@code transcribe} —— 转写业务；语音识别端口 {@code SpeechTranscriber} 与它同包；</li>
 *   <li>{@code score}      —— 评分业务；两个评分端口与它同包。{@code rubric} 是版本与条目目录；</li>
 *   <li>{@code adapter}    —— 端口的实现，当前只有 {@code adapter.fake} 假实现；</li>
 *   <li>{@code web}        —— HTTP 边界：Controller、请求/响应模型、请求语义校验。</li>
 * </ul>
 *
 * <p><b>依赖方向（如实描述，不是理想图）</b>：
 * <ul>
 *   <li>{@code web} 的 Controller 依赖业务服务，<b>反向不成立</b>——没有任何 Controller 被业务层依赖；</li>
 *   <li>但 {@code web} 同时持有对外契约的**数据载体**（请求/响应模型）与请求语义校验，
 *       业务服务、端口与适配器**可以复用**这些扁平模型。这是刻意的取舍：再包一层领域对象
 *       只会多一份需要同步的结构。因此「业务 → web」的引用是常态，不要把这里读成严格分层；</li>
 *   <li>{@code adapter} 依赖它实现的端口（端口与各自业务同包），业务层不依赖 {@code adapter}。</li>
 * </ul>
 *
 * <p>任务状态**不落库**：结果只在内存有效期内可读，进程重启后客户端会收到 {@code PROCESS_RESTARTED}
 * 并重交材料。业务结果由移动端保存，服务端不长期持有儿童数据。
 *
 * <p>数据边界：无数据库表；儿童音频与回答文本不落盘、不写日志、不进备份。
 */
package com.earlylearning.early_learning_server.ai;
