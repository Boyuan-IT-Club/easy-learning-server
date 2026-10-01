package com.earlylearning.early_learning_server.ai.interfaces.dto;

import java.util.List;

import com.earlylearning.early_learning_server.ai.domain.task.BusinessType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 故事评分请求。
 *
 * @param rubricVersion 可省略；省略时服务端采用固定配置的版本，并在任务凭据里返回实际版本
 * @param textConfirmed 必须为 true：教师已明确确认文本
 * @param confirmedText 经教师确认的原话；空字符串表示已确认无回应，不代表缺失或转写失败
 * @param storyContext  经确认的故事依据，不接受占位内容
 * @param contentItems  本次叙事的全部图片分组，按配置顺序
 * @param images        必须为每个分组引用的图片逐一提供真实图片或确认说明，不得只发 file_code
 */
public record StoryScoringRequest(

        @JsonProperty("request_id") String requestId,
        @JsonProperty("input_revision") String inputRevision,
        @JsonProperty("business_type") BusinessType businessType,
        @JsonProperty("activity_id") String activityId,
        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("confirmed_text") String confirmedText,
        @JsonProperty("text_confirmed") Boolean textConfirmed,
        @JsonProperty("story_context") String storyContext,
        @JsonProperty("content_items") List<ContentItem> contentItems,
        @JsonProperty("images") List<ImageContext> images) {
}
