package com.earlylearning.early_learning_server.ai.task;

import java.time.Instant;

/**
 * 内存态任务。
 *
 * <p>状态**不落库**，进程重启即丢失——这是契约要求的：结果只在内存有效期内可读。
 * 重启后任务号查不到，客户端会收到 404 {@code TASK_NOT_FOUND}（{@code PROCESS_RESTARTED}
 * 能表达但触发不了：内存表里没有"曾经存在"的痕迹）。
 *
 * <p>状态转换是 {@code synchronized} 的：一次转换要改多个字段，逐个 volatile 不足以保证读到的是一致快照。
 */
public class AiTask {

    private final String taskId;
    private final String requestId;
    private final String inputRevision;
    private final TaskKind taskKind;
    private final String rubricVersion;
    private final Instant submittedAt;
    private final BusinessType businessType;
    private final String activityId;

    /** 重试会加一，因此不是 final。 */
    private int attemptNo;
    private TaskStage stage;
    private Instant updatedAt;
    private FailedStage failedStage;
    private TaskFailure failure;
    private Instant resultExpiresAt;
    private Object result;

    public AiTask(String taskId,
                  String requestId,
                  String inputRevision,
                  TaskKind taskKind,
                  int attemptNo,
                  String rubricVersion,
                  BusinessType businessType,
                  String activityId,
                  Instant submittedAt) {
        this.taskId = taskId;
        this.requestId = requestId;
        this.inputRevision = inputRevision;
        this.taskKind = taskKind;
        this.attemptNo = attemptNo;
        this.rubricVersion = rubricVersion;
        this.businessType = businessType;
        this.activityId = activityId;
        this.submittedAt = submittedAt;
        this.stage = TaskStage.QUEUED;
        this.updatedAt = submittedAt;
    }

    /**
     * 按原输入重跑：序号加一，回到排队状态并清掉上次的失败信息。**task_id 不变**——
     * 契约要求"重启原 task_id"。
     */
    public synchronized void restart(Instant now) {
        this.attemptNo++;
        this.stage = TaskStage.QUEUED;
        this.updatedAt = now;
        this.failedStage = null;
        this.failure = null;
        this.result = null;
        this.resultExpiresAt = null;
    }

    /**
     * 结果过期发生在产物生成之后：失败阶段要跟随任务类型。
     *
     * <p>原来硬编码 {@code SCORE}，于是转写任务过期时会报 {@code failed_stage=SCORE}——
     * 客户端按这个字段归因就会归错。
     */
    private FailedStage failedStageFor(TaskKind kind) {
        return kind == TaskKind.TRANSCRIPTION ? FailedStage.TRANSCRIBE : FailedStage.SCORE;
    }

    public synchronized void moveTo(TaskStage next, Instant now) {
        this.stage = next;
        this.updatedAt = now;
    }

    public synchronized void fail(FailedStage where, TaskFailure taskFailure, Instant now) {
        this.stage = TaskStage.FAILED;
        this.failedStage = where;
        this.failure = taskFailure;
        this.result = null;
        this.resultExpiresAt = null;
        this.updatedAt = now;
    }

    /**
     * 仅当任务仍处于 {@code expected} 阶段时才写入成功结果。
     *
     * <p>给执行器用：超时后看门狗已把任务落成失败，而工作线程可能随后才返回——
     * 直接写成功会把失败状态覆盖掉，客户端会看到"超时了但结果是成功的"。
     *
     * @return 是否真的写入了
     */
    public synchronized boolean succeedIfIn(TaskStage expected, Object taskResult, Instant expiresAt, Instant now) {
        if (stage != expected) {
            return false;
        }
        succeed(taskResult, expiresAt, now);
        return true;
    }

    public synchronized void succeed(Object taskResult, Instant expiresAt, Instant now) {
        this.stage = TaskStage.SUCCEEDED;
        this.result = taskResult;
        this.resultExpiresAt = expiresAt;
        this.failure = null;
        this.failedStage = null;
        this.updatedAt = now;
    }

    /**
     * 结果超过有效期时转为失败并清掉结果。
     *
     * @return 本次调用是否触发了过期转换
     */
    public synchronized boolean expireIfNeeded(Instant now) {
        if (stage != TaskStage.SUCCEEDED || resultExpiresAt == null || resultExpiresAt.isAfter(now)) {
            return false;
        }
        fail(failedStageFor(taskKind), TaskFailure.resultExpired(), now);
        return true;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInputRevision() {
        return inputRevision;
    }

    public TaskKind getTaskKind() {
        return taskKind;
    }

    public synchronized int getAttemptNo() {
        return attemptNo;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public String getActivityId() {
        return activityId;
    }

    public synchronized TaskStage getStage() {
        return stage;
    }

    public synchronized Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized FailedStage getFailedStage() {
        return failedStage;
    }

    public synchronized TaskFailure getFailure() {
        return failure;
    }

    public synchronized Instant getResultExpiresAt() {
        return resultExpiresAt;
    }

    public synchronized Object getResult() {
        return result;
    }
}
