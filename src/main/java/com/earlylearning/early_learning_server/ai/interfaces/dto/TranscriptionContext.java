package com.earlylearning.early_learning_server.ai.interfaces.dto;
import com.earlylearning.early_learning_server.ai.domain.transcription.TranscriptionTarget;

import com.earlylearning.early_learning_server.ai.domain.task.Attempt;
import com.earlylearning.early_learning_server.ai.domain.task.BusinessType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 转写请求里的 {@code context}（一个 application/json 部分）。
 *
 * <p>{@code attempt} 是契约的 {@link Attempt} 枚举（{@code BEFORE_HINT}/{@code AFTER_HINT}），
 * 不是序号——写成 {@code Integer} 会让契约合规的客户端提交直接 400。
 *
 * <p>契约按 {@code target} 分成两种形态：故事录音只需要前五项；单题录音还要 {@code question_id} 与
 * {@code attempt}，且不允许出现前者不需要的字段（契约是 {@code additionalProperties: false}）。
 */
public record TranscriptionContext(

        @JsonProperty("request_id") String requestId,
        @JsonProperty("input_revision") String inputRevision,
        @JsonProperty("business_type") BusinessType businessType,
        @JsonProperty("activity_id") String activityId,
        @JsonProperty("target") TranscriptionTarget target,
        @JsonProperty("question_id") String questionId,
        @JsonProperty("attempt") Attempt attempt) {
}
