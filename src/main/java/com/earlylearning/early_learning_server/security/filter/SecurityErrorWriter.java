package com.earlylearning.early_learning_server.security.filter;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 在安全过滤器里直接写出错误响应。
 *
 * <p>过滤器在 DispatcherServlet 之前执行，{@code GlobalExceptionHandler} 管不到这里，
 * 所以要自己按同一个包络 {@code {code, message, data: null}} 写出。
 */
public final class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, BusinessException error) throws IOException {
        write(response, error.getHttpStatus().value(), error.getErrorCode(), error.getMessage());
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code.defaultStatus().value(), code, code.defaultMessage());
    }

    private void write(HttpServletResponse response, int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }
}
