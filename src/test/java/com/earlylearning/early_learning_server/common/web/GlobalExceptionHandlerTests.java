package com.earlylearning.early_learning_server.common.web;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.earlylearning.early_learning_server.common.logging.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证「异常 → 契约响应」这条链路，以及 traceId 的生成与回写。
 *
 * <p>用 standalone MockMvc，不启 Spring 上下文：这里要验的是映射规则本身。
 */
class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void businessExceptionMapsToContractStatusAndCode() throws Exception {
        mockMvc.perform(post("/fixture/resource-in-use"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.details.file_code").value("CF_a1b2"));
    }

    @Test
    void dependencyFailureMapsTo503() throws Exception {
        mockMvc.perform(post("/fixture/dependency-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void runtimeExceptionIsNotMappedTo503() throws Exception {
        mockMvc.perform(get("/fixture/illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutStack() throws Exception {
        mockMvc.perform(get("/fixture/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("RuntimeException"))));
    }

    @Test
    void missingTraceIdIsGeneratedAndEchoed() throws Exception {
        mockMvc.perform(get("/fixture/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER))
                        .as("生成的 traceId 不应为空")
                        .isNotBlank());
    }

    @Test
    void validTraceIdIsReused() throws Exception {
        mockMvc.perform(get("/fixture/ok").header(TraceIdFilter.TRACE_ID_HEADER, "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "trace-abc-123"));
    }

    @Test
    void invalidTraceIdIsDiscardedAndRegenerated() throws Exception {
        String evil = "bad\nid";
        mockMvc.perform(get("/fixture/ok").header(TraceIdFilter.TRACE_ID_HEADER, evil))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER))
                        .as("含换行的 traceId 必须被拒绝，否则会污染日志")
                        .isNotEqualTo(evil));
    }

    /**
     * 请求期间的日志事件必须带 traceId。
     *
     * <p>与之配套的是 logback 的 {@code %X{traceId}}：MDC 有值、pattern 读该键，两者齐备日志才带上追踪标识。
     */
    @Test
    void logEventsDuringRequestCarryTraceId() throws Exception {
        ch.qos.logback.classic.Logger handlerLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            var result = mockMvc.perform(get("/fixture/unexpected")).andReturn();
            String headerTraceId = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

            assertThat(appender.list).as("未捕获异常应产生一条 ERROR 日志").isNotEmpty();
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .as("每条日志事件的 MDC 都必须带 traceId")
                            .containsEntry(TraceIdFilter.MDC_KEY, headerTraceId));
        } finally {
            handlerLogger.detachAppender(appender);
            appender.stop();
        }
    }

    /** 仅用于触发各类异常的测试夹具，不参与生产代码。 */
    @RestController
    @RequestMapping("/fixture")
    static class ThrowingController {

        @GetMapping("/ok")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }

        @PostMapping("/resource-in-use")
        ResponseEntity<Void> resourceInUse() {
            throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "文件正被引用",
                    ApiErrorDetails.atFile("CF_a1b2"));
        }

        @PostMapping("/dependency-down")
        ResponseEntity<Void> dependencyDown() {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "upload failed",
                    new IOException("connection reset"));
        }

        @GetMapping("/illegal-state")
        ResponseEntity<Void> illegalState() {
            throw new IllegalStateException("programming error");
        }

        @GetMapping("/unexpected")
        ResponseEntity<Void> unexpected() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/spring-exception")
        ResponseEntity<Void> springException() {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "teapot");
        }
    }
}
