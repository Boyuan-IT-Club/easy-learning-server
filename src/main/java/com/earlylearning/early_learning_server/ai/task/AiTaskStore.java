package com.earlylearning.early_learning_server.ai.task;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 任务的内存登记处。
 *
 * <p>任务状态不落库（契约与 `ai/package-info.java` 都明确）。保留条数有上限，
 * 超出后丢弃最早提交的——契约提到的"元数据清理后返回 404"由此体现，同时避免内存无界增长。
 *
 * <p>这里同时保存 **request_id → (task_id, 输入指纹)**：AI 提交的幂等判据必须以内存中的任务为准，
 * 不能用持久化的幂等表——进程重启后任务没了、表却还在，会返回指向已消失任务的凭据。
 * 三个提交接口共用同一份映射，因此"同一 request_id 跨接口复用"会因指纹不同而被判为冲突。
 */
@Component
public class AiTaskStore {

    private final Map<String, AiTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    private final int maxRetained;

    public AiTaskStore(@Value("${ai.task.max-retained:500}") int maxRetained) {
        this.maxRetained = maxRetained;
    }

    public void save(AiTask task) {
        tasks.put(task.getTaskId(), task);
        evictOldestIfNeeded();
    }

    /** 记录一次提交：同一个 request_id 之后只认这里的指纹。 */
    public void rememberSubmission(String requestId, String taskId, String inputFingerprint) {
        submissions.put(requestId, new Submission(taskId, inputFingerprint));
    }

    /** @return 任务；不存在或已被清理时为 null */
    public AiTask find(String taskId) {
        return tasks.get(taskId);
    }

    public Optional<Submission> findSubmission(String requestId) {
        return Optional.ofNullable(submissions.get(requestId));
    }

    private void evictOldestIfNeeded() {
        while (tasks.size() > maxRetained) {
            tasks.values().stream()
                    .min(Comparator.comparing(AiTask::getSubmittedAt))
                    .map(AiTask::getTaskId)
                    .ifPresent(this::drop);
        }
    }

    private void drop(String taskId) {
        tasks.remove(taskId);
        submissions.values().removeIf(submission -> submission.taskId().equals(taskId));
    }

    /** 一次提交留下的判据：任务编号与输入指纹。 */
    public record Submission(String taskId, String inputFingerprint) {
    }
}
