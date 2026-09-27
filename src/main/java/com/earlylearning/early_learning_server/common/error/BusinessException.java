package com.earlylearning.early_learning_server.common.error;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带契约里的错误码，由全局处理器翻译成响应。
 *
 * <p>状态码默认取 {@link ErrorCode#defaultStatus()}。同一个码需要出现在两个状态时
 * （如 {@link ErrorCode#LICENSE_REVOKED}）用带 {@code httpStatus} 的构造器显式指定。
 *
 * <p>依赖失败同样用它表达：{@code new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, message, cause)}，
 * 由适配层保留原始 cause。**不要**把通用运行时异常也映射成 503——那会把编程错误伪装成依赖不可用。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final transient ApiErrorDetails details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultStatus(), null, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, errorCode.defaultStatus(), message, null, null);
    }

    /** 只带定位信息、不自定义说明：错误码自带的措辞就是契约要的。 */
    public BusinessException(ErrorCode errorCode, ApiErrorDetails details) {
        this(errorCode, errorCode.defaultStatus(), null, details, null);
    }

    public BusinessException(ErrorCode errorCode, String message, ApiErrorDetails details) {
        this(errorCode, errorCode.defaultStatus(), message, details, null);
    }

    /** 保留原始原因。 */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, errorCode.defaultStatus(), message, null, cause);
    }

    public BusinessException(ErrorCode errorCode,
                             HttpStatus httpStatus,
                             String message,
                             ApiErrorDetails details,
                             Throwable cause) {
        super(message != null ? message : errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public ApiErrorDetails getDetails() {
        return details;
    }
}
