package com.earlylearning.early_learning_server.ai.dto;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单题评分请求：一次提交只评一次回答。
 *
 * <p>{@code attempt} 标明这次评的是提示前还是提示后； {@code BEFORE_HINT}
 * 只评价提示前回答，因此请求里根本不携带另一次的回答文本——两次作答各自是独立输入。
 *
 * @param rubricVersion 可省略；省略时服务端采用固定配置的版本，并在任务凭据里返回实际版本
 * @param confirmedText 本次经教师确认的原话；空字符串表示已确认无回应，不代表缺失或转写失败
 * @param textConfirmed 必须为 true：教师已明确确认
 * @param storyContext  经确认的故事依据，不接受占位内容
 * @param question      题目与预设提示；{@code hint} 允许为空字符串（这道题不设提示）
 * @param attempt       提示前 / 提示后
 * @param images        纯文本故事依据足够时可空；需要识图时必须提供全部相关图片或确认说明
 */
public record AnswerScoringRequest(

        @JsonProperty("request_id") String requestId,
        @JsonProperty("input_revision") String inputRevision,
        @JsonProperty("business_type") BusinessType businessType,
        @JsonProperty("activity_id") String activityId,
        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("confirmed_text") String confirmedText,
        @JsonProperty("text_confirmed") Boolean textConfirmed,
        @JsonProperty("story_context") String storyContext,
        @JsonProperty("question") ScoringQuestion question,
        @JsonProperty("attempt") Attempt attempt,
        @JsonProperty("images") List<ImageContext> images) {
}
