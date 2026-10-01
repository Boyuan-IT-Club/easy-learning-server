package com.earlylearning.early_learning_server.common.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 访问日志：每个请求一行，记方法、路径、状态码、耗时与调用方身份。
 *
 * <p>只记路径，不记查询串和请求体：查询串里有筛选关键字，请求体里有密码、激活码与凭证。
 * 排在 {@link TraceIdFilter} 之后，日志行自带 traceId；调用方身份由鉴权过滤器写进 MDC，
 * 请求结束时由 {@link TraceIdFilter} 统一清理。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        // 异常一路抛到这里时响应还没写，按 500 记
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            String principal = MDC.get(TraceIdFilter.PRINCIPAL_MDC_KEY);
            log.info("{} {} status={} costMs={} principal={}", request.getMethod(), request.getRequestURI(),
                    status, costMs, principal == null ? "-" : principal);
        }
    }
}
