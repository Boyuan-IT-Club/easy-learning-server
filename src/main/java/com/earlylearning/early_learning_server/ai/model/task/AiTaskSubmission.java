package com.earlylearning.early_learning_server.ai.model.task;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 转写、故事评分、单题评分三个提交接口共用的提交语义。
 *
 * <ul>
 *   <li>首次序号必须为 0,网络重发同号幂等返回同一凭据;</li>
 *   <li>FAILED 且可重试时序号加一,重启原 task_id;</li>
 *   <li>旧序号、跳号、运行中递增 → 409 {@code TASK_RETRY_CONFLICT};</li>
 *   <li>同 request_id 换了输入 → 409 {@code IDEMPOTENCY_CONFLICT}。</li>
 * </ul>
 *
 * <p>幂等判据来自内存中的任务登记处,不使用持久化幂等表:进程重启后任务即失效,
 * 持久化的判据会指向已消失的任务。
 */
@Component
public class AiTaskSubmission {

    /** 首次提交的序号。三个提交服务共用，写在这里避免各自再抄一份。 */
    public static final int FIRST_ATTEMPT = 0;

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
     * 解析本次提交，并在需要新建时同一步完成登记。
     *
     * <p>「同标识同输入不重复创建」。把 {@code resolve} 与登记分开调用是 check-then-act：
     * 两个并发请求（客户端超时重发很常见）可以同时看到"还没有这个 request_id"，
     * 于是各建一个任务、各跑一次识别；重试路径则会连跳两次序号。
     *
     * <p>整段互斥:提交是低频的人机操作,临界区只做几次 map 查找、没有 I/O。
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

    /**
     * 提交并执行：解析 + 登记 + 分派一气呵成，三个提交服务共用的模板。
     *
     * <p>CREATE 与 RESTART 都会执行工作（{@code run}），区别只在 RESTART 先走 {@code restart}
     * （默认实现：先做服务特有的校验、再 {@code task.restart} 把任务拉回队列）。评分服务把
     * 「重试沿用任务记录的评分依据版本」的校验放进 restart——它在任务状态被改写之前执行，
     * 校验不过时任务保持 FAILED，下一次同样的重试还能得到同样的拒绝。
     *
     * @param newTask 仅在 CREATE 时调用，用于构造并登记新任务
     * @param run     CREATE 与 RESTART 时执行的工作，入参是任务、返回更新后的任务
     * @param restart RESTART 时的收尾（默认：{@code task.restart(now)} 后执行 {@code run}）
     */
    public AiTask submitAndRun(String requestId,
                               String inputFingerprint,
                               int retryAttempt,
                               Supplier<AiTask> newTask,
                               Function<AiTask, AiTask> run,
                               Function<AiTask, AiTask> restart) {
        Outcome outcome = resolveAndRegister(requestId, inputFingerprint, retryAttempt, newTask);
        return switch (outcome.action()) {
            case CREATE -> run.apply(outcome.task());
            case RESTART -> restart.apply(outcome.task());
            case REPLAY -> outcome.task();
        };
    }

    /** {@link #submitAndRun} 的默认 restart：把任务拉回队列后按原样执行工作。 */
    public AiTask submitAndRun(String requestId,
                               String inputFingerprint,
                               int retryAttempt,
                               Supplier<AiTask> newTask,
                               Function<AiTask, AiTask> run) {
        return submitAndRun(requestId, inputFingerprint, retryAttempt, newTask, run,
                task -> {
                    task.restart(java.time.Instant.now());
                    return run.apply(task);
                });
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
