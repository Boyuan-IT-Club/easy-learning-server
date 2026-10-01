package com.earlylearning.early_learning_server.common.idempotency;

/**
 * 幂等域：决定哪些请求共享同一个键空间。
 *
 * <p>官方文件上传与账号类写接口走这张幂等表；含凭证的结果（激活码、Token）另见 {@link SensitiveIdempotency}。**AI 三个提交接口不走这里**——
 * 它们用内存里的任务登记处（见 {@code ai.task.AiTaskSubmission}）：
 * 幂等判据必须和任务同在内存，否则进程重启后表还在、任务没了，
 * 会返回指向已消失任务的凭据。原设计里的 {@code ai.task.submit} 键空间因此作废。
 */
public enum IdempotencyScope {

    ADMIN_FILE_UPLOAD("admin.file.upload"),
    ADMIN_ACCOUNT_CREATE("admin.account.create"),
    LICENSE_CREATE("license.create"),
    AUTH_REGISTER("auth.register"),
    AUTH_REFRESH("auth.refresh");

    private final String value;

    IdempotencyScope(String value) {
        this.value = value;
    }

    /** 落库值；改动会让既有幂等记录失效。 */
    public String value() {
        return value;
    }
}
