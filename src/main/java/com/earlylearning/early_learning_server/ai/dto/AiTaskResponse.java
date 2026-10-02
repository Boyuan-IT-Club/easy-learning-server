package com.earlylearning.early_learning_server.ai.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailure;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的任务查询响应。
 *
 * <p>五形态共用一个 DTO：类级 {@code NON_NULL} 让与当前阶段无关的字段不出现（契约的每个形态都是
 * {@code additionalProperties: false}），而 {@code rubric_version} 在契约里是必填但可空，
 * 所以单独标注为始终输出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiTaskResponse(

        @JsonProperty("task_id") String taskId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("input_revision") String inputRevision,
        @JsonProperty("task_kind") TaskKind taskKind,
        @JsonProperty("attempt_no") int attemptNo,
        @JsonProperty("rubric_version") @JsonInclude(JsonInclude.Include.ALWAYS) String rubricVersion,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("stage") TaskStage stage,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("failed_stage") FailedStage failedStage,
        @JsonProperty("failure") TaskFailure failure,
        @JsonProperty("result_expires_at") String resultExpiresAt,
        @JsonProperty("result") Object result,
        @JsonProperty("business_type") BusinessType businessType,
        @JsonProperty("activity_id") String activityId) {

    public static AiTaskResponse from(AiTask task) {
        // 逐字段调 synchronized getter，两次取字段之间写线程可能插入一次完整状态转换，
        // 于是读出 stage=SUCCEEDED 但 result=null 这类组合，违反契约的五形态。整段读才一致。
        synchronized (task) {
            return new AiTaskResponse(
                task.getTaskId(),
                task.getRequestId(),
                task.getInputRevision(),
                task.getTaskKind(),
                task.getAttemptNo(),
                task.getRubricVersion(),
                isoUtc(task.getSubmittedAt()),
                task.getStage(),
                isoUtc(task.getUpdatedAt()),
                task.getFailedStage(),
                task.getFailure(),
                isoUtc(task.getResultExpiresAt()),
                task.getResult(),
                task.getBusinessType(),
                task.getActivityId());
        }
    }

    private static String isoUtc(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);
    }
}
