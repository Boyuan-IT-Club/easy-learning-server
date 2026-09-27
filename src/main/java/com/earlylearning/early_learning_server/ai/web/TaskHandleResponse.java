package com.earlylearning.early_learning_server.ai.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.earlylearning.early_learning_server.ai.task.TaskKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的 `TaskHandle`：提交成功后返回的任务凭据。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskHandleResponse(

        @JsonProperty("task_id") String taskId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("input_revision") String inputRevision,
        @JsonProperty("task_kind") TaskKind taskKind,
        @JsonProperty("attempt_no") int attemptNo,
        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("business_type") BusinessType businessType,
        @JsonProperty("activity_id") String activityId) {

    public static TaskHandleResponse from(AiTask task) {
        // 逐字段调 synchronized getter，两次取字段之间写线程可能插入一次完整状态转换，
        // 于是读出 stage=SUCCEEDED 但 result=null 这类组合，违反契约的五形态。整段读才一致。
        synchronized (task) {
            return new TaskHandleResponse(
                task.getTaskId(),
                task.getRequestId(),
                task.getInputRevision(),
                task.getTaskKind(),
                task.getAttemptNo(),
                task.getRubricVersion(),
                isoUtc(task.getSubmittedAt()),
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
