package com.earlylearning.early_learning_server.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 把异常翻译成的响应形状。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler}：参数绑定、消息解析、路径不存在等框架异常由 Spring 识别，
 * 这里只覆盖需要契约特定错误码的分支，其余框架异常统一套上响应包络。
 *
 * <p>500 的响应体里只有错误码与简短说明，不回传堆栈；堆栈只进日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：状态码与错误码都已确定。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("业务异常 {} {} code={}", request.getMethod(), request.getRequestURI(), code.name(), ex);
        } else {
            log.warn("业务异常 {} {} code={} status={}",
                    request.getMethod(), request.getRequestURI(), code.name(), ex.getHttpStatus().value());
        }
        return build(ex.getHttpStatus(), code, ex.getMessage(), ex.getDetails(), null);
    }

    /** 方法级参数校验失败（@Validated 触发）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleParamValidation(ConstraintViolationException ex) {
        String fieldPath = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> "/" + violation.getPropertyPath())
                .orElse(null);
        log.warn("参数校验失败 field={}", fieldPath);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, null,
                fieldPath == null ? null : ApiErrorDetails.atField(fieldPath), null);
    }

    /** 其它 multipart 解析失败，属于请求本身不合法。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipart(MultipartException ex) {
        log.warn("multipart 解析失败: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, null, null, null);
    }

    /** 兜底：未预期的异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未捕获异常 {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, null, null, null);
    }

    /** 请求体字段校验失败（@Valid 触发）：给出失败字段的 JSON Pointer。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String fieldPath = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "/" + error.getField())
                .orElse(null);
        log.warn("请求体校验失败 field={}", fieldPath);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, null,
                fieldPath == null ? null : ApiErrorDetails.atField(fieldPath), headers);
    }

    /**
     * URL 里的百分号写坏了（Tomcat 解码失败）——这是请求不合法，不是服务端错误。
     *
     * <p>Tomcat 会记一条 "Character decoding failed ... has been ignored"，Spring 把它归到 5xx；
     * 契约对该路径声明的是 400，所以这里显式改判。
     */
    @ExceptionHandler(org.apache.tomcat.util.http.InvalidParameterException.class)
    public ResponseEntity<Object> handleInvalidParameter(org.apache.tomcat.util.http.InvalidParameterException ex,
                                                         WebRequest request) {
        log.warn("URL 参数解码失败 {}", request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI() : "");
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                ErrorCode.INVALID_REQUEST.defaultMessage(), null, HttpHeaders.EMPTY);
    }

    /** 框架异常统一走这里：沿用 Spring 判断出的状态码，换成本项目的错误码与包络。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request) {
        ErrorCode code = codeFor(status);
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
        if (status.is5xxServerError()) {
            log.error("框架异常 {} code={}", path, code.name(), ex);
        } else {
            log.warn("框架异常 {} code={} status={}", path, code.name(), status.value());
        }
        // message 用错误码的默认文案：它非空（minLength 1、pattern \\S），
        // 传 null 会让客户端拿到 "message": null
        return build(status, code, code.defaultMessage(), null, headers);
    }

    /** 框架异常的状态码到契约错误码。契约未覆盖的状态（如 405）沿用状态码本身。 */
    private ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.INVALID_REQUEST;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 503 -> ErrorCode.DEPENDENCY_UNAVAILABLE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    private static ResponseEntity<Object> build(HttpStatusCode status,
                                                ErrorCode code,
                                                String message,
                                                ApiErrorDetails details,
                                                HttpHeaders headers) {
        return new ResponseEntity<>(ApiResponse.fail(code, message, details), headers, status);
    }
}
