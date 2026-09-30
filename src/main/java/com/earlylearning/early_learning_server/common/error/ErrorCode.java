package com.earlylearning.early_learning_server.common.error;

import org.springframework.http.HttpStatus;

/**
 * 接口响应的 code 取值空间。
 *
 * <p>取值与《早期学习困难儿童筛查与干预系统 HTTP API》契约中的 code 字面量逐一对应；
 * 枚举名即 code 字符串，因此 {@link #valueOf(String)} 可直接用契约里的值反查。</p>
 *
 * <p>{@code defaultStatus} 只是默认值。同一个 code 在不同上下文可能对应不同 HTTP 状态
 * （例如 {@link #LICENSE_REVOKED} 同时出现在 403 与 409），
 * <b>实际状态由抛出位置选用的异常类型决定</b>，不由本枚举唯一决定。</p>
 */

public enum ErrorCode {

    /** 成功。HTTP 状态由具体接口决定（200 / 201 / 202）。 */
    OK(HttpStatus.OK, "成功"),

    // ---------- 400 ----------
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数或编码不合法"),

    // ---------- 401 ----------
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "缺少凭证"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "凭证失效"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "凭证已过期"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号密码不正确"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "刷新凭证无效"),
    RECOVERY_CODE_INVALID(HttpStatus.UNAUTHORIZED, "恢复码无效"),

    // ---------- 403 ----------
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账号不可用"),
    LICENSE_REVOKED(HttpStatus.FORBIDDEN, "激活码已被撤销"),
    AUTH_ROLE_MISMATCH(HttpStatus.FORBIDDEN, "凭证类型错误"),
    RESOURCE_FORBIDDEN(HttpStatus.FORBIDDEN, "无资源权限"),
    DEVICE_MISMATCH(HttpStatus.FORBIDDEN, "设备与账号绑定不一致"),

    // ---------- 404 ----------
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "任务不存在"),

    // ---------- 409 ----------
    USERNAME_EXISTS(HttpStatus.CONFLICT, "用户名已存在"),
    LICENSE_UNAVAILABLE(HttpStatus.CONFLICT, "激活码不可用"),
    DEVICE_ALREADY_BOUND(HttpStatus.CONFLICT, "设备或账号已绑定"),
    ADMIN_LAST_ACTIVE(HttpStatus.CONFLICT, "至少保留一个可用管理员"),
    CONTENT_VERSION_EXISTS(HttpStatus.CONFLICT, "内容版本已存在"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "版本冲突"),
    RESOURCE_IN_USE(HttpStatus.CONFLICT, "资源被引用，不能删除"),
    RESOURCE_NOT_READY(HttpStatus.CONFLICT, "资源尚未就绪"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "同一请求标识的输入已改变"),
    SENSITIVE_RESULT_EXPIRED(HttpStatus.CONFLICT, "敏感结果已失效"),
    TASK_RETRY_CONFLICT(HttpStatus.CONFLICT, "任务重试序号冲突"),

    // ---------- 410 ----------
    FILE_DELETED(HttpStatus.GONE, "文件已删除"),

    // ---------- 413 ----------
    // 码值由；HttpStatus.PAYLOAD_TOO_LARGE 已废弃（RFC 9110 改名），故映射到 CONTENT_TOO_LARGE。
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "超出部署允许的上限"),
    AUDIO_DURATION_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "音频超出时长上限"),
    IMAGE_LIMIT_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "图片数量超出上限"),

    // ---------- 415 ----------
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的格式"),
    CONTENT_TYPE_MISMATCH(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "声明类型与实际内容不符"),

    // ---------- 422 ----------
    INVALID_ACTIVITY_CONFIG(HttpStatus.UNPROCESSABLE_CONTENT, "活动配置不合法"),
    STORY_NARRATION_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "缺少故事叙述"),
    INVALID_RESOURCE_REFERENCE(HttpStatus.UNPROCESSABLE_CONTENT, "资源引用无效"),
    INVALID_RUBRIC_MAPPING(HttpStatus.UNPROCESSABLE_CONTENT, "评分条目映射不完整"),
    INVALID_GRAMMAR_REFERENCE(HttpStatus.UNPROCESSABLE_CONTENT, "语法要素引用无效"),
    INVALID_GLOSS_OFFSET(HttpStatus.UNPROCESSABLE_CONTENT, "词语位置无效"),
    TEXT_NOT_CONFIRMED(HttpStatus.UNPROCESSABLE_CONTENT, "文本尚未确认"),
    INCOMPLETE_IMAGE_CONTEXT(HttpStatus.UNPROCESSABLE_CONTENT, "图片上下文不完整"),
    INVALID_VERSION_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT, "版本切换不合法"),

    // ---------- 429 ----------
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),

    // ---------- 500 ----------
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "内部错误"),

    // ---------- 503 ----------
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用"),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "依赖暂不可用"),
    RUBRIC_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "评分配置不可用");

    private final HttpStatus defaultStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus defaultStatus, String defaultMessage) {
        this.defaultStatus = defaultStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus defaultStatus() {
        return defaultStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}