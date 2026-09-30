package com.earlylearning.early_learning_server.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个请求建立 traceId，写入 MDC 与响应头。
 *
 * <p>这样每一条日志都能关联到具体请求，客户端报障时也能把 traceId 带回来。
 *
 * <p>排序在最前，保证后续所有日志（含异常处理器的 500 日志）都能带上 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 客户端可用此头传入自己的追踪标识，也会在响应里原样带回。 */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    /** 日志 pattern 里通过 %X{traceId} 引用。 */
    public static final String MDC_KEY = "traceId";

    /** 来源 IP，供审计与限流读取（见 {@link RequestOrigin}）；不进日志 pattern。 */
    public static final String CLIENT_IP_MDC_KEY = "clientIp";

    /**
     * 只接受安全字符且限长。
     *
     * <p>不信任客户端传入值：直接落进日志会被注入换行等内容，过长还会撑爆日志。
     * 不匹配就自己生成一个。
     */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(MDC_KEY, traceId);
        MDC.put(CLIENT_IP_MDC_KEY, request.getRemoteAddr());
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池会复用线程，不清理会导致下一个请求串上本次的 traceId
            MDC.remove(MDC_KEY);
            MDC.remove(CLIENT_IP_MDC_KEY);
        }
    }

    private static String resolveTraceId(String incoming) {
        if (incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
