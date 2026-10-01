package com.earlylearning.early_learning_server.ai.client.task;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 任务登记处的内存实现。
 *
 * <p>保留条数有上限，超出后丢弃最早提交的——契约提到的"元数据清理后返回 404"由此体现，
 * 同时避免内存无界增长。
 */
@Component
public class InMemoryAiTaskStore implements AiTaskStore {

    private final Map<String, AiTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    private final int maxRetained;

    public InMemoryAiTaskStore(@Value("${ai.task.max-retained:500}") int maxRetained) {
        this.maxRetained = maxRetained;
    }

    @Override
    public void save(AiTask task) {
        tasks.put(task.getTaskId(), task);
        evictOldestIfNeeded();
    }

    @Override
    public AiTask find(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public void rememberSubmission(String requestId, String taskId, String inputFingerprint) {
        submissions.put(requestId, new Submission(taskId, inputFingerprint));
    }

    @Override
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
}
