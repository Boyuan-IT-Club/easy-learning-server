package com.earlylearning.early_learning_server.ai.model.task;

/**
 * 单题作答的两次提交标识。
 *
 * <p>契约的定性是「独立任务、独立评分，不混合两次文本」：
 * {@code BEFORE_HINT} 仅评价提示前回答，不引入提示后文本。
 *
 * <p>它决定的是评的是哪一次回答，不是"哪次更晚"——两次提交各有自己的 request_id 与任务，
 * 完成顺序不受保证，所以下游取分一律按本标识判断，不看时间先后。
 */
public enum Attempt {

    BEFORE_HINT,
    AFTER_HINT
}
