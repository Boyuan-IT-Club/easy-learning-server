/**
 * 出站端口：会被替换的接口。
 *
 * <p>这三个接口是"调外部能力"的缝：转写、故事评分、单题评分。
 * **接口只声明契约，不关心实现**；默认实现是 {@code ai.adapter.fake} 下的假实现，
 * 接入真实模型时提供同类型 Bean 并标 {@code @Primary} 覆盖即可，业务代码不改。
 *
 * <p>约定：实现抛 {@code AiTaskFailedException} 表示可预期的失败（带任务失败码）；
 * 异常 message 不得内嵌识别正文或儿童原话——它会进日志。
 */
package com.earlylearning.early_learning_server.ai.port;
