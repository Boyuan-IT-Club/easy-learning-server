package com.earlylearning.early_learning_server.common.logging;

import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessLogFilterTests {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void 记方法路径状态与调用方_不记查询串() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        request.setQueryString("username=secret-keyword");
        FilterChain chain = (req, res) -> {
            // 鉴权过滤器在链路中间写入调用方
            MDC.put(TraceIdFilter.PRINCIPAL_MDC_KEY, "admin:3");
            ((HttpServletResponse) res).setStatus(200);
        };

        new TraceIdFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> new AccessLogFilter().doFilter(req, res, chain));

        List<String> lines = messages();
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("GET /admin/users status=200 costMs=").endsWith("principal=admin:3")
                .doesNotContain("secret-keyword");
        assertThat(MDC.get(TraceIdFilter.PRINCIPAL_MDC_KEY)).as("请求结束后清理").isNull();
    }

    @Test
    void 未认证记横线_异常抛出时按500记() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> new AccessLogFilter().doFilter(request, new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(messages()).singleElement().asString()
                .startsWith("POST /api/auth/register status=500").endsWith("principal=-");
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
