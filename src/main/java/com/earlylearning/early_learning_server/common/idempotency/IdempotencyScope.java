package com.earlylearning.early_learning_server.common.idempotency;
import com.earlylearning.early_learning_server.ai.domain.task.AiTaskSubmission;

/**
 * 幂等域：决定哪些请求共享同一个键空间。
 *
 * <p>官方文件上传、评估材料 ZIP 发布与账号类写接口走这张幂等表；账号类里含凭证的结果另见 {@link SensitiveIdempotency}。AI 三个提交接口不走这里——
 * 它们用内存里的任务登记处（见 {@code ai.task.AiTaskSubmission}）：
 * 幂等判据必须和任务同在内存，否则进程重启后表还在、任务没了，
 * 会返回指向已消失任务的凭据。原设计里的 {@code ai.task.submit} 键空间因此作废。
 */
public enum IdempotencyScope {

    ADMIN_FILE_UPLOAD("admin.file.upload"),
    ASSESSMENT_MATERIAL_PUBLISH("assessment.material.publish"),
    AUTH_REGISTER("auth.register"),
    AUTH_RECOVER("auth.recover"),
    LICENSE_BATCH_CREATE("license.batch.create"),
    TEACHER_RECOVERY_CODE("teacher.recovery-code"),
    ADMIN_CREATE("admin.create");

    private final String value;

    IdempotencyScope(String value) {
        this.value = value;
    }

    /** 落库值；改动会让既有幂等记录失效。 */
    public String value() {
        return value;
    }
}
