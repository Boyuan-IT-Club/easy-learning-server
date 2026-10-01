package com.earlylearning.early_learning_server.ai.model.task;

import java.util.Optional;

/**
 * 任务登记处的端口。
 *
 * <p>任务状态目前不落库（契约明确），内存实现是
 * {@code client/task/InMemoryAiTaskStore}；按 ADR-0005「异步任务要求服务端持久化任务状态」，
 * 将来落库时新增实现即可，提交与查询的语义不动。
 *
 * <p>这里同时保存 request_id → (task_id, 输入指纹)：AI 提交的幂等判据必须与任务同寿——
 * 进程重启后任务没了、映射却还在，会返回指向已消失任务的凭据。三个提交接口共用同一份映射，
 * 因此"同一 request_id 跨接口复用"会因指纹不同而被判为冲突。
 */
public interface AiTaskStore {

    void save(AiTask task);

    /** @return 任务；不存在或已被清理时为 null */
    AiTask find(String taskId);

    /** 记录一次提交：同一个 request_id 之后只认这里的指纹。 */
    void rememberSubmission(String requestId, String taskId, String inputFingerprint);

    Optional<Submission> findSubmission(String requestId);

    /** 一次提交留下的判据：任务编号与输入指纹。 */
    record Submission(String taskId, String inputFingerprint) {
    }
}
