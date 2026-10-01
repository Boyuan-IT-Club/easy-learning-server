package com.earlylearning.early_learning_server.ai.client.task;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.ai.client.task.AiTaskProperties;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailure;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;

import lombok.extern.slf4j.Slf4j;

/**
 * AI 任务的异步执行：把结果写回内存里的任务。
 *
 * <p>共用同一份实现——转写与评分只是"做什么"不同，失败落成任务状态、成功写结果与过期时间的处理完全一样。
 *
 * <p>三条边界都落在这里：
 * <ul>
 *   <li><b>队列有界</b>：任务是按段提交的录音/文本，积压时每个排队项都占着几十 MB 的儿童数据。
 *       队列满时拒绝并落成可重试的任务失败，而不是无限排队把堆吃光。</li>
 *   <li><b>有超时</b>：适配器挂死时任务会永远停在"进行中"，客户端只能一直轮询；超时落成
 *       {@code MODEL_TIMEOUT} 且标记可重试，客户端至少能重试。</li>
 *   <li><b>日志不含正文</b>：儿童音频与文本"不写日志"。所以只记任务 id、失败码与异常类型名，
 *       既不记异常 message（适配器可能把识别原文塞进去），也不打堆栈（堆栈首行就是 toString，含 message）。</li>
 * </ul>
 */
@Component
@Slf4j
public class AiTaskRunner {

    private static final int POOL_SIZE = 2;
    /** 队列容量：两倍线程数。再多的请求直接落成可重试失败，比堆里堆着几百 MB 儿童数据好。 */
    private static final int QUEUE_CAPACITY = 4;
    private static final String DEFAULT_FAILURE_MESSAGE = "任务执行失败";

    /** 超时用的调度器：一个线程足够，只做"到点检查"。 */
    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ai-task-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    private final ExecutorService pool = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "ai-task-runner");
                thread.setDaemon(true);
                return thread;
            });

    private final long resultTtlSeconds;
    private final long timeoutSeconds;

    public AiTaskRunner(AiTaskProperties properties) {
        this.resultTtlSeconds = properties.resultTtlSeconds();
        this.timeoutSeconds = properties.timeoutSeconds();
    }

    /**
     * 异步执行一次任务。
     *
     * @param workingStage           执行期间对外可见的阶段
     * @param failedStage            失败归属于哪个环节
     * @param unexpectedFailureCode  未预期异常对应的失败码（可预期失败用 {@link AiTaskFailedException} 自带）
     * @param work                   实际工作；返回值会成为任务结果
     */
    public void run(AiTask task,
                    TaskStage workingStage,
                    FailedStage failedStage,
                    TaskFailureCode unexpectedFailureCode,
                    Callable<Object> work) {
        try {
            Future<Object> running = pool.submit(() -> {
                Instant startedAt = Instant.now();
                task.moveTo(workingStage, startedAt);
                try {
                    Object result = work.call();
                    // 超时后工作线程可能才返回：只有仍在执行阶段才写成功，否则会覆盖掉失败
                    long costMs = Duration.between(startedAt, Instant.now()).toMillis();
                    if (task.succeedIfIn(workingStage, result,
                            Instant.now().plusSeconds(resultTtlSeconds), Instant.now())) {
                        log.info("任务完成 taskId={} stage={} costMs={}", task.getTaskId(), workingStage, costMs);
                    } else {
                        log.info("任务已按超时处理，丢弃迟到的结果 taskId={} costMs={}", task.getTaskId(), costMs);
                    }
                    return result;
                } catch (AiTaskFailedException ex) {
                    // 只记失败码，不记 message：适配器可能把识别原文写进 message
                    log.warn("任务失败 taskId={} code={} retryable={}",
                            task.getTaskId(), ex.getFailureCode(), ex.isRetryable());
                    fail(task, failedStage, ex.getFailureCode(), ex.isRetryable());
                    throw ex;
                } catch (RuntimeException ex) {
                    // 只记异常类型，不打堆栈：堆栈首行是 toString，含 message
                    log.error("任务出现未预期错误 taskId={} type={}", task.getTaskId(), ex.getClass().getName());
                    fail(task, failedStage, unexpectedFailureCode, true);
                    throw ex;
                }
            });
            watchdog.schedule(() -> {
                if (running.isDone()) {
                    return;
                }
                log.warn("任务超时 taskId={} timeoutSeconds={}", task.getTaskId(), timeoutSeconds);
                running.cancel(true);
                fail(task, failedStage, TaskFailureCode.MODEL_TIMEOUT, true);
            }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            // 队列已满：契约的任务失败码里没有"排队已满"，就近用 TASK_TIMEOUT 并标记可重试
            log.warn("任务队列已满，落成可重试失败 taskId={}", task.getTaskId());
            fail(task, failedStage, TaskFailureCode.TASK_TIMEOUT, true);
        }
    }

    private void fail(AiTask task, FailedStage failedStage, TaskFailureCode code, boolean retryable) {
        task.fail(failedStage, new TaskFailure(code, DEFAULT_FAILURE_MESSAGE, retryable), Instant.now());
    }

    /** 停机钩子；同时供测试在构造了自己的执行器后显式释放。 */
    @PreDestroy
    public void shutdown() {
        watchdog.shutdownNow();
        pool.shutdownNow();
    }
}
