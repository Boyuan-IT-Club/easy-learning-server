package com.earlylearning.early_learning_server.ai.task;

/**
 * 任务失败信息。
 *
 * @param code      失败码，取值见 {@link TaskFailureCode}
 * @param message   脱敏说明，不附加儿童原话
 * @param retryable 是否可按原输入重试；输入改变须换 request_id 与 input_revision
 */
public record TaskFailure(TaskFailureCode code, String message, boolean retryable) {

    /** 结果超过内存有效期。 */
    public static TaskFailure resultExpired() {
        return new TaskFailure(TaskFailureCode.RESULT_EXPIRED, "结果已超过可读有效期", true);
    }

    /** 处理进程重启，任务未能完成。 */
    public static TaskFailure processRestarted() {
        return new TaskFailure(TaskFailureCode.PROCESS_RESTARTED, "处理进程已重启", true);
    }
}
