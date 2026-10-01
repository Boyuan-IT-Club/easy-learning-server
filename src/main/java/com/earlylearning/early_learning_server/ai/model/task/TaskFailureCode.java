package com.earlylearning.early_learning_server.ai.model.task;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 任务失败码。
 *
 * <p><b>与 {@code ErrorCode} 是两套独立空间</b>：这些值出现在 HTTP 200 响应的 {@code data.failure.code} 里，
 * 不是响应包络的 {@code code}。两者都有的 {@code RUBRIC_UNAVAILABLE} 是不同语境下的同名值。
 */
public enum TaskFailureCode {

    ASR_FAILED,
    MODEL_TIMEOUT,
    MODEL_OUTPUT_INVALID,
    RUBRIC_UNAVAILABLE,
    PROCESS_RESTARTED,
    RESULT_EXPIRED,
    TASK_TIMEOUT
}
