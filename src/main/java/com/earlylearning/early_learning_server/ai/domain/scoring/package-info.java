/**
 * 评分领域。按业务分成两块：
 *
 * <ul>
 *   <li>{@code story} — 故事评分：端口、命令/输入、AIScore v2 结果与运行时校验；</li>
 *   <li>{@code question} — 单题（提示前/提示后）评分：端口、命令/输入、结果与校验。</li>
 * </ul>
 *
 * <p>本包根下只剩两类共用件：提交与解析的值类型（{@code ImageRef}/{@code ScoringGroup}/
 * {@code ScoringImage} 等）和两块共用的证据机制（{@code EvidenceLocator} 定位、{@code EvidenceValidator} 校验）、
 * 模型元信息与输出异常、输入上限。
 */
package com.earlylearning.early_learning_server.ai.domain.scoring;
