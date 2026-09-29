package com.earlylearning.early_learning_server.ai.domain.task;

/**
 * 任务执行失败。
 *
 * <p>由执行器抛出并携带任务失败码（不是响应包络的错误码），最终落成任务的 {@code stage=FAILED}。
 * 失败说明只进日志；给客户端的是固定文案，避免把模型或识别服务返回的原文带出去。
 */
public class AiTaskFailedException extends RuntimeException {

    private final TaskFailureCode failureCode;
    private final boolean retryable;

    public AiTaskFailedException(TaskFailureCode failureCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    /** 没有原始异常可带时用这个，省得调用方写一个无意义的 {@code null}。 */
    public AiTaskFailedException(TaskFailureCode failureCode, String message, boolean retryable) {
        this(failureCode, message, retryable, null);
    }

    public TaskFailureCode getFailureCode() {
        return failureCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
