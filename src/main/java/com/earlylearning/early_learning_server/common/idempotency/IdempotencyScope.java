package com.earlylearning.early_learning_server.common.idempotency;

/**
 * 幂等域：决定哪些请求共享同一个键空间。
 *
 * <p>AI 三个提交接口共用 {@link #AI_TASK_SUBMIT}，因为契约要求同一教师的 request_id 跨接口不可复用。
 */
public enum IdempotencyScope {

    ADMIN_FILE_UPLOAD("admin.file.upload"),
    AI_TASK_SUBMIT("ai.task.submit");

    private final String value;

    IdempotencyScope(String value) {
        this.value = value;
    }

    /** 落库值；改动会让既有幂等记录失效。 */
    public String value() {
        return value;
    }

    /**
     * AI 提交接口的键。
     *
     * <p>{@code request_id} 只在同一教师内唯一，因此键必须带教师维度，
     * 否则两个教师碰巧用同一个标识会互相判定为「输入已改变」。
     */
    public static String aiSubmitKey(long userId, String requestId) {
        return userId + ":" + requestId;
    }
}
