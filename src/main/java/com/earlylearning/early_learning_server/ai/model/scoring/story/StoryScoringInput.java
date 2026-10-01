package com.earlylearning.early_learning_server.ai.model.scoring.story;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringInput;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.ScoringGroup;

/**
 * 故事评分的领域输入：图片已经解析成模型能直接用的东西。
 *
 * <p>与单题评分的 {@link AnswerScoringInput} 同构，理由也相同：解析放在提交的同步路径上，
 * 图片内容有问题（base64 非法、超过字节上限、编号不可读）立刻以 400/413/404 一类响应暴露，
 * 而不是等任务跑起来才落成失败——那时失败码的语义对不上"请求不合法"。
 *
 * @param confirmedText 教师确认的儿童原话；空字符串表示"已确认无回应"，与缺失不同
 * @param storyContext  教师确认的故事依据
 * @param contentItems  本次叙事的图片分组，顺序即展示顺序
 * @param images        各分组引用的图片，已解析；与 {@code contentItems} 的引用一一对应
 */
public record StoryScoringInput(String confirmedText,
                                String storyContext,
                                List<ScoringGroup> contentItems,
                                List<ScoringImage> images) {
}
