package com.earlylearning.early_learning_server.ai.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.earlylearning.early_learning_server.ai.client.task.InMemoryAiTaskStore;
import com.earlylearning.early_learning_server.ai.controller.AiTaskController;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailure;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.earlylearning.early_learning_server.ai.service.task.AiTaskService;
import com.earlylearning.early_learning_server.ai.service.task.impl.AiTaskServiceImpl;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 任务的状态机与查询。
 *
 * <p>任务状态在内存里，所以这个测试不需要 Spring 上下文，也不依赖数据库。
 */
class AiTaskTests {

    private static final int MAX_RETAINED = 3;

    private final AiTaskStore store = new InMemoryAiTaskStore(MAX_RETAINED);
    private final AiTaskService service = new AiTaskServiceImpl(store);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AiTaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queuedTaskReportsRunningShapeWithoutFailureOrResult() throws Exception {
        AiTask task = register(TaskKind.TRANSCRIPTION);

        mockMvc.perform(get("/api/ai/tasks/{id}", task.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.stage").value("QUEUED"))
                .andExpect(jsonPath("$.data.task_kind").value("TRANSCRIPTION"))
                // 契约里 rubric_version 必填但可空：转写任务为 null，键仍须出现
                .andExpect(content().string(containsString("\"rubric_version\":null")))
                .andExpect(content().string(not(containsString("failure"))))
                .andExpect(content().string(not(containsString("result"))));
    }

    @Test
    void stageProgressesThroughRunningStages() {
        AiTask task = register(TaskKind.STORY_SCORING);

        task.moveTo(TaskStage.TRANSCRIBING, Instant.now());
        assertThat(service.query(task.getTaskId()).getStage()).isEqualTo(TaskStage.TRANSCRIBING);

        task.moveTo(TaskStage.SCORING, Instant.now());
        assertThat(service.query(task.getTaskId()).getStage()).isEqualTo(TaskStage.SCORING);
    }

    @Test
    void succeededTaskCarriesResultAndExpiry() throws Exception {
        AiTask task = register(TaskKind.TRANSCRIPTION);
        task.succeed(java.util.Map.of("transcript", "识别结果"),
                Instant.now().plus(30, ChronoUnit.MINUTES), Instant.now());

        mockMvc.perform(get("/api/ai/tasks/{id}", task.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.transcript").value("识别结果"))
                .andExpect(jsonPath("$.data.result_expires_at").isNotEmpty())
                .andExpect(content().string(not(containsString("failed_stage"))));
    }

    @Test
    void expiredResultBecomesFailedWithResultExpired() throws Exception {
        AiTask task = register(TaskKind.TRANSCRIPTION);
        // 结果的有效期已经过去
        task.succeed(java.util.Map.of("transcript", "旧结果"),
                Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now());

        mockMvc.perform(get("/api/ai/tasks/{id}", task.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.code").value("RESULT_EXPIRED"))
                .andExpect(jsonPath("$.data.failure.retryable").value(true))
                // 过期后结果不再可读
                .andExpect(content().string(not(containsString("旧结果"))));
    }

    @Test
    void failedTaskReportsFailedStageAndFailure() throws Exception {
        AiTask task = register(TaskKind.TRANSCRIPTION);
        task.fail(FailedStage.TRANSCRIBE,
                new TaskFailure(TaskFailureCode.ASR_FAILED, "识别失败", true), Instant.now());

        mockMvc.perform(get("/api/ai/tasks/{id}", task.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FAILED"))
                .andExpect(jsonPath("$.data.failed_stage").value("TRANSCRIBE"))
                .andExpect(jsonPath("$.data.failure.code").value("ASR_FAILED"));
    }

    @Test
    void processRestartedIsReportedAsAFailureNotAsAnEnvelopeError() throws Exception {
        AiTask task = register(TaskKind.ANSWER_SCORING);
        task.fail(FailedStage.SCORE, TaskFailure.processRestarted(), Instant.now());

        // 契约：失败仍是 HTTP 200、外层 code=OK，失败信息在 data 里
        mockMvc.perform(get("/api/ai/tasks/{id}", task.getTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.failure.code").value("PROCESS_RESTARTED"));
    }

    @Test
    void expiredTranscriptionReportsTranscribeStageNotScore() {
        // 结果过期发生在产物生成之后：失败阶段要跟随任务类型。
        // 曾经硬编码 SCORE，于是转写任务过期时报 failed_stage=SCORE，客户端按它归因会归错。
        AiTask task = new AiTask(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), TaskKind.TRANSCRIPTION, 0, null,
                BusinessType.CLASSROOM, "act_1", Instant.now());
        task.succeed("识别结果", Instant.now().minusSeconds(1), Instant.now());

        assertThat(task.expireIfNeeded(Instant.now())).isTrue();
        assertThat(task.getFailure().code()).isEqualTo(TaskFailureCode.RESULT_EXPIRED);
        assertThat(task.getFailedStage()).isEqualTo(FailedStage.TRANSCRIBE);
    }

    @Test
    void unknownButWellFormedTaskIs404WithTaskNotFound() throws Exception {
        String unknown = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.query(unknown))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);

        mockMvc.perform(get("/api/ai/tasks/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void malformedTaskIdIs400Not404() throws Exception {
        // 契约把 task_id 声明为 Uuid：格式不对属于请求不合法，
        // 客户端要能区分"我传错了"与"任务真的没了"。
        assertThatThrownBy(() -> service.query("no-such-task"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        mockMvc.perform(get("/api/ai/tasks/{id}", "no-such-task"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.field_path").value("/task_id"));
    }

    @Test
    void oldestTaskIsEvictedOnceStoreIsFull() {
        AiTask first = register(TaskKind.TRANSCRIPTION);
        register(TaskKind.TRANSCRIPTION);
        register(TaskKind.TRANSCRIPTION);
        // 上限是 3，此时最早的仍在
        assertThat(service.query(first.getTaskId()).getStage()).isEqualTo(TaskStage.QUEUED);

        // 第 4 个进来后最早的被清理，查询结果等同于"元数据已被清理"
        AiTask newest = register(TaskKind.TRANSCRIPTION);
        assertThat(service.query(newest.getTaskId()).getStage()).isEqualTo(TaskStage.QUEUED);
        assertThatThrownBy(() -> service.query(first.getTaskId()))
                .isInstanceOf(BusinessException.class);
    }

    private AiTask register(TaskKind kind) {
        Instant submitted = Instant.now();
        AiTask task = new AiTask(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                kind,
                0,
                kind == TaskKind.TRANSCRIPTION ? null : "RUBRIC_V1",
                BusinessType.ASSESSMENT,
                "ACT_DEMO_1",
                submitted);
        return service.register(task);
    }
}
