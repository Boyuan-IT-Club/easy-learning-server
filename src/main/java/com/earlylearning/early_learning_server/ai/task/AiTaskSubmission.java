package com.earlylearning.early_learning_server.ai.task;

import java.util.Optional;
import java.util.function.Supplier;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 三个 AI 提交接口共用的提交语义。
 *
 * <p>契约对转写、故事评分、单题评分的要求完全相同，所以这段逻辑只能有一份：
 * <ul>
 *   <li>首次序号必须为 0，网络重发同号幂等返回同一凭据；</li>
 *   <li>FAILED 且可重试时序号加一 → **重启原 task_id**；</li>
 *   <li>旧序号、跳号、运行中递增 → 409 {@code TASK_RETRY_CONFLICT}；</li>
 *   <li>同 request_id 换了输入 → 409 {@code IDEMPOTENCY_CONFLICT}。</li>
 * </ul>
 *
 * <p>幂等判据来自**内存中的任务登记处**，不是持久化的幂等表——进程重启后任务没了、表却还在，
 * 会返回指向已消失任务的凭据。
 */
@Component
public class AiTaskSubmission {

    private static final int FIRST_ATTEMPT = 0;

    private final AiTaskStore store;

    public AiTaskSubmission(AiTaskStore store) {
        this.store = store;
    }

    /**
     * 判断本次提交该做什么。
     *
     * @param inputFingerprint 覆盖本次请求全部输入的指纹
     * @throws BusinessException 输入改变、序号不合法，或任务已被清理
     */
    public Outcome resolve(String requestId, String inputFingerprint, int retryAttempt) {
        Optional<AiTaskStore.Submission> existing = store.findSubmission(requestId);
        if (existing.isEmpty()) {
            if (retryAttempt != FIRST_ATTEMPT) {
                // 没有这个 request_id 的历史，却报了非 0 的序号——跳号
                throw new BusinessException(ErrorCode.TASK_RETRY_CONFLICT);
            }
            return new Outcome(null, Action.CREATE);
        }

        AiTaskStore.Submission submission = existing.get();
        if (!submission.inputFingerprint().equals(inputFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        AiTask task = store.find(submission.taskId());
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        int currentAttempt = task.getAttemptNo();
        if (retryAttempt == currentAttempt) {
            return new Outcome(task, Action.REPLAY);
        }
        if (retryAttempt == currentAttempt + 1 && canRetry(task)) {
            return new Outcome(task, Action.RESTART);
        }
        throw new BusinessException(ErrorCode.TASK_RETRY_CONFLICT);
    }

    /**
     * 解析本次提交，并在需要新建时**同一步**完成登记。
     *
     * <p>契约要求「同标识同输入不重复创建」。把 {@code resolve} 与登记分开调用是 check-then-act：
     * 两个并发请求（客户端超时重发很常见）可以同时看到"还没有这个 request_id"，
     * 于是各建一个任务、各跑一次识别；重试路径则会连跳两次序号。
     *
     * <p>这里整段互斥。提交是低频的人机操作，临界区只做几次 map 查找、没有 I/O，
     * 直接互斥比按 requestId 细粒度加锁更简单，也观察不到争用。
     *
     * @param newTask 仅在 CREATE 时调用，用于构造新任务
     */
    public synchronized Outcome resolveAndRegister(String requestId,
                                                   String inputFingerprint,
                                                   int retryAttempt,
                                                   Supplier<AiTask> newTask) {
        Outcome outcome = resolve(requestId, inputFingerprint, retryAttempt);
        if (outcome.action() != Action.CREATE) {
            return outcome;
        }
        AiTask task = newTask.get();
        store.save(task);
        store.rememberSubmission(requestId, task.getTaskId(), inputFingerprint);
        return new Outcome(task, Action.CREATE);
    }

    private boolean canRetry(AiTask task) {
        return task.getStage() == TaskStage.FAILED
                && task.getFailure() != null
                && task.getFailure().retryable();
    }

    public enum Action {
        CREATE,
        REPLAY,
        RESTART
    }

    public record Outcome(AiTask task, Action action) {
    }
}
