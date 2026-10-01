package com.earlylearning.early_learning_server.common.logging;

import org.slf4j.MDC;

/**
 * 当前请求的来源信息，由 {@link TraceIdFilter} 在请求入口写入 MDC。
 *
 * <p>让应用层拿到 IP 与 traceId 而不依赖 HTTP 类型（限流需要 IP）。
 * 请求线程之外（启动任务、异步任务）调用时返回 null。
 *
 * <p>IP 取的是直连地址：部署在 Nginx 之后时需要开启 {@code server.forward-headers-strategy}，
 * 否则所有请求都会显示为 Nginx 的地址。
 */
public final class RequestOrigin {

    private RequestOrigin() {
    }

    public static String clientIp() {
        return MDC.get(TraceIdFilter.CLIENT_IP_MDC_KEY);
    }

    public static String traceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}
