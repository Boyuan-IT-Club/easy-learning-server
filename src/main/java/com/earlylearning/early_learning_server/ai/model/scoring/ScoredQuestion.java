package com.earlylearning.early_learning_server.ai.model.scoring;

/**
 * 单题评分的题目上下文。
 *
 * @param questionId 问答活动内唯一且稳定的题目编号
 * @param text       经业务确认的问题原文
 * @param hint       预设提示，允许空字符串；不得混入评分规则
 */
public record ScoredQuestion(String questionId, String text, String hint) {
}
