package com.earlylearning.early_learning_server.ai.domain.task;

/**
 * 任务阶段。
 *
 * <p>决定响应形态：{@link #QUEUED}/{@link #TRANSCRIBING}/{@link #SCORING} 是进行中，
 * {@link #FAILED} 带失败信息，{@link #SUCCEEDED} 带结果与结果过期时间。
 */
public enum TaskStage {

    QUEUED,
    TRANSCRIBING,
    SCORING,
    FAILED,
    SUCCEEDED
}
