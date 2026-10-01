package com.earlylearning.early_learning_server.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 事务提交后执行；当前没有事务时立即执行。
 *
 * <p>用途：Redis 这类不参与数据库事务的写入。先写 Redis 再回滚，会留下指向不存在数据的 Token；
 * 推迟到提交后，回滚时就什么都不会发生。
 *
 * <p>提交后的任务失败只记日志、不上抛：数据库已经提交，此时再抛异常只会让客户端误以为操作失败。
 * 调用方要保证"任务没执行"是可以自愈的（例如 access 没写进 Redis，客户端会在 401 后自动刷新）。
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(String description, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("提交后任务失败 task={} cause={}", description, e.getClass().getSimpleName(), e);
                }
            }
        });
    }
}
