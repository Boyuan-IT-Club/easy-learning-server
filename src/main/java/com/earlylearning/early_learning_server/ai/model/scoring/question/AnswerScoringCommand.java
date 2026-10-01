package com.earlylearning.early_learning_server.ai.model.scoring.question;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoredQuestion;
import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;

/**
 * 单题评分的应用服务输入：HTTP 请求经 controller 层校验并映射后的形态。一次提交只评一次回答。
 *
 * <p>{@code attempt} 标明这次评的是提示前还是提示后； {@code BEFORE_HINT}
 * 只评价提示前回答，因此这里根本不携带另一次的回答文本——两次作答各自是独立输入。
 *
 * @param rubricVersion 可省略；省略时服务端采用固定配置的版本，并在任务凭据里返回实际版本
 * @param confirmedText 本次经教师确认的原话；空字符串表示已确认无回应，不代表缺失或转写失败
 * @param storyContext  经确认的故事依据，不接受占位内容
 * @param question      题目与预设提示；{@code hint} 允许为空字符串（这道题不设提示）
 * @param attempt       提示前 / 提示后
 * @param images        纯文本故事依据足够时可空；需要识图时必须提供全部相关图片或确认说明
 */
public record AnswerScoringCommand(String requestId,
                                   String inputRevision,
                                   BusinessType businessType,
                                   String activityId,
                                   String rubricVersion,
                                   String confirmedText,
                                   String storyContext,
                                   ScoredQuestion question,
                                   Attempt attempt,
                                   List<ImageRef> images) {
}
