package com.earlylearning.early_learning_server.ai;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.earlylearning.early_learning_server.ai.transcribe.SpeechTranscriber;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.earlylearning.early_learning_server.ai.task.FailedStage;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskKind;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.transcribe.AiTranscriptionLimits;
import com.earlylearning.early_learning_server.ai.transcribe.AiTranscriptionService;
import com.earlylearning.early_learning_server.ai.transcribe.AudioDurationParser;
import com.earlylearning.early_learning_server.ai.transcribe.AudioValidator;
import com.earlylearning.early_learning_server.ai.transcribe.TranscriptionResult;
import com.earlylearning.early_learning_server.ai.web.AiTranscriptionController;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import com.earlylearning.early_learning_server.storage.MediaTypeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 转写提交：{@code retry_attempt} 状态机、幂等、以及异步执行后的任务状态。
 *
 * <p>全部在内存里完成，不需要 Spring 上下文，也不连数据库与 OSS。
 */
class AiTranscriptionTests {

    private static final int MAX_RETAINED = 100;
    private static final int HUNDRED_MILLISECOND_WAV = 1600;

    private final AiTaskStore store = new AiTaskStore(MAX_RETAINED);
    private final AtomicInteger transcriptionCalls = new AtomicInteger();
    private volatile SpeechTranscriber transcriber = (audio, mimeType) -> {
        transcriptionCalls.incrementAndGet();
        return "识别结果";
    };
    private AiTranscriptionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transcriptionCalls.set(0);
        // 每次都取当前的 transcriber，便于单个用例替换行为
        AiTaskRunner runner = new AiTaskRunner(1800, 600);
        service = new AiTranscriptionService(new AiTaskSubmission(store), runner,
                (audio, mimeType) -> transcriber.transcribe(audio, mimeType));
        AudioValidator validator = new AudioValidator(new MediaTypeDetector(), new AudioDurationParser(),
                new AiTranscriptionLimits(50_000_000, 600_000));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiTranscriptionController(validator, service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void firstSubmissionIsAcceptedAndRunsToSucceeded() throws Exception {
        String requestId = UUID.randomUUID().toString();

        String body = mockMvc.perform(submit(requestId, 0))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.request_id").value(requestId))
                .andExpect(jsonPath("$.data.task_kind").value("TRANSCRIPTION"))
                .andExpect(jsonPath("$.data.attempt_no").value(0))
                .andReturn().getResponse().getContentAsString();

        String taskId = body.replaceAll(".*\"task_id\":\"([^\"]+)\".*", "$1");
        AiTask task = awaitStage(taskId, TaskStage.SUCCEEDED);
        assertThat(((TranscriptionResult) task.getResult()).transcript()).isEqualTo("识别结果");
    }

    @Test
    void resendingTheSameRequestIsIdempotentAndDoesNotRunAgain() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String firstTaskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(firstTaskId, TaskStage.SUCCEEDED);

        String secondTaskId = taskIdOf(submitAndAccept(requestId, 0));

        assertThat(secondTaskId).isEqualTo(firstTaskId);
        assertThat(transcriptionCalls.get()).isEqualTo(1);
    }

    @Test
    void sameRequestIdWithDifferentAudioIsConflict() throws Exception {
        String requestId = UUID.randomUUID().toString();
        awaitStage(taskIdOf(submitAndAccept(requestId, 0)), TaskStage.SUCCEEDED);

        assertThatThrownBy(() -> service.submit(wav(3200), "audio/wav",
                context(requestId, UUID.randomUUID().toString()), 0))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void firstSubmissionWithNonZeroAttemptIsRetryConflict() throws Exception {
        mockMvc.perform(submit(UUID.randomUUID().toString(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_RETRY_CONFLICT"));
    }

    @Test
    void failedRetryableTaskCanBeRestartedWithNextAttemptAndKeepsTaskId() throws Exception {
        String requestId = UUID.randomUUID().toString();
        // 第一次识别失败，且标记为可重试
        transcriber = (audio, mimeType) -> {
            throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "临时故障", true, null);
        };
        String taskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(taskId, TaskStage.FAILED);
        assertThat(store.find(taskId).getFailure().code()).isEqualTo(TaskFailureCode.ASR_FAILED);
        assertThat(store.find(taskId).getFailure().retryable()).isTrue();

        // 这次能成功；序号加一，task_id 不变
        transcriber = (audio, mimeType) -> "重试后的结果";
        String restartedTaskId = taskIdOf(submitAndAccept(requestId, 1));

        assertThat(restartedTaskId).isEqualTo(taskId);
        AiTask task = awaitStage(taskId, TaskStage.SUCCEEDED);
        assertThat(task.getAttemptNo()).isEqualTo(1);
        assertThat(((TranscriptionResult) task.getResult()).transcript()).isEqualTo("重试后的结果");
    }

    @Test
    void skippingAnAttemptIsRetryConflict() throws Exception {
        String requestId = UUID.randomUUID().toString();
        transcriber = (audio, mimeType) -> {
            throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "临时故障", true, null);
        };
        String taskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(taskId, TaskStage.FAILED);

        // 从 0 直接跳到 2
        assertThatThrownBy(() -> service.submit(wav(HUNDRED_MILLISECOND_WAV), "audio/wav",
                context(requestId, null), 2))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TASK_RETRY_CONFLICT);
    }

    @Test
    void nonRetryableFailureCannotBeRestarted() throws Exception {
        String requestId = UUID.randomUUID().toString();
        transcriber = (audio, mimeType) -> {
            throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "格式无法识别", false, null);
        };
        String taskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(taskId, TaskStage.FAILED);

        assertThatThrownBy(() -> service.submit(wav(HUNDRED_MILLISECOND_WAV), "audio/wav",
                context(requestId, null), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TASK_RETRY_CONFLICT);
    }

    @Test
    void bumpingAttemptWhileStillRunningIsRetryConflict() throws Exception {
        String requestId = UUID.randomUUID().toString();
        CountDownLatch blocked = new CountDownLatch(1);
        transcriber = (audio, mimeType) -> {
            try {
                blocked.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "慢速结果";
        };
        String taskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(taskId, TaskStage.TRANSCRIBING);
        try {
            assertThatThrownBy(() -> service.submit(wav(HUNDRED_MILLISECOND_WAV), "audio/wav",
                    context(requestId, null), 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_RETRY_CONFLICT);
        } finally {
            blocked.countDown();
        }
    }

    @Test
    void oldAttemptIsRetryConflict() throws Exception {
        String requestId = UUID.randomUUID().toString();
        transcriber = (audio, mimeType) -> {
            throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "临时故障", true, null);
        };
        String taskId = taskIdOf(submitAndAccept(requestId, 0));
        awaitStage(taskId, TaskStage.FAILED);
        transcriber = (audio, mimeType) -> "重试后的结果";
        taskIdOf(submitAndAccept(requestId, 1));
        awaitStage(taskId, TaskStage.SUCCEEDED);

        // 再用旧序号 0 提交
        assertThatThrownBy(() -> service.submit(wav(HUNDRED_MILLISECOND_WAV), "audio/wav",
                context(requestId, null), 0))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TASK_RETRY_CONFLICT);
    }

    @Test
    void unsupportedAudioFormatIsRejectedWithTheFormatReasonOverHttp() throws Exception {
        byte[] ogg = new byte[64];
        System.arraycopy("OggS".getBytes(StandardCharsets.US_ASCII), 0, ogg, 0, 4);

        mockMvc.perform(multipart("/api/ai/transcribe")
                        .file(new MockMultipartFile("audio", "voice.ogg", "audio/ogg", ogg))
                        .file(contextPart(context(UUID.randomUUID().toString(), null)))
                        .param("retry_attempt", "0"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("无法校验时长")));
    }

    @Test
    void hungAdapterBecomesARetryableTimeoutFailureAndLateResultDoesNotOverwriteIt() throws Exception {
        AiTaskStore localStore = new AiTaskStore(MAX_RETAINED);
        CountDownLatch release = new CountDownLatch(1);
        AiTranscriptionService localService = new AiTranscriptionService(new AiTaskSubmission(localStore),
                new AiTaskRunner(1800, 1), // 超时 1 秒
                (audio, mimeType) -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "回来得太晚了";
                });
        String requestId = UUID.randomUUID().toString();

        String taskId = localService.submit(wav(HUNDRED_MILLISECOND_WAV), "audio/wav",
                context(requestId, null), 0).taskId();

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && localStore.find(taskId).getStage() != TaskStage.FAILED) {
            Thread.sleep(50);
        }
        AiTask task = localStore.find(taskId);
        assertThat(task.getStage()).isEqualTo(TaskStage.FAILED);
        assertThat(task.getFailure().code()).isEqualTo(TaskFailureCode.MODEL_TIMEOUT);
        assertThat(task.getFailure().retryable()).isTrue();

        // 挂死的工作线程随后返回：不能把失败覆盖成成功
        release.countDown();
        Thread.sleep(300);
        assertThat(localStore.find(taskId).getStage()).isEqualTo(TaskStage.FAILED);
    }

    @Test
    void overflowingQueueBecomesARetryableFailureInsteadOfQueueingForever() throws Exception {
        AiTaskRunner runner = new AiTaskRunner(1800, 600);
        CountDownLatch release = new CountDownLatch(1);
        AiTaskStore localStore = new AiTaskStore(MAX_RETAINED);
        Callable<Object> blocking = () -> {
            release.await(10, TimeUnit.SECONDS);
            return null;
        };
        // 占满 2 个工作线程 + 4 个队列位
        for (int i = 0; i < 6; i++) {
            runner.run(newPendingTask(localStore, i), TaskStage.TRANSCRIBING, FailedStage.TRANSCRIBE,
                    TaskFailureCode.ASR_FAILED, blocking);
        }

        AiTask overflow = newPendingTask(localStore, 7);
        runner.run(overflow, TaskStage.TRANSCRIBING, FailedStage.TRANSCRIBE,
                TaskFailureCode.ASR_FAILED, () -> null);

        assertThat(overflow.getStage()).isEqualTo(TaskStage.FAILED);
        assertThat(overflow.getFailure().code()).isEqualTo(TaskFailureCode.TASK_TIMEOUT);
        assertThat(overflow.getFailure().retryable()).isTrue();

        release.countDown();
        runner.shutdown();
    }

    @Test
    void concurrentSameRequestSubmissionsCreateOnlyOneTaskAndRunOnce() throws Exception {
        // 契约：同标识同输入不重复创建。resolve 与登记若分成两步（check-then-act），
        // 并发提交会各建一个任务、各跑一次识别。
        String requestId = UUID.randomUUID().toString();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return taskIdOf(mockMvc.perform(submit(requestId, 0))
                        .andReturn().getResponse().getContentAsString());
            }));
        }
        start.countDown();
        java.util.Set<String> taskIds = new java.util.HashSet<>();
        for (java.util.concurrent.Future<String> future : futures) {
            taskIds.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(taskIds).hasSize(1);
        assertThat(transcriptionCalls.get()).isEqualTo(1);
    }

    @Test
    void questionAnsweringContextAcceptsTheContractAttemptEnum() throws Exception {
        // 契约里 attempt 是 Attempt 枚举（BEFORE_HINT/AFTER_HINT），不是序号。
        // 这条必须走 Jackson 反序列化才测得出类型错配——曾经写成 Integer，契约合规的提交直接 400。
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(multipart("/api/ai/transcribe")
                        .file(new MockMultipartFile("audio", "voice.wav", "audio/wav", wav(HUNDRED_MILLISECOND_WAV)))
                        .file(questionContextPart(requestId, "\"BEFORE_HINT\""))
                        .param("retry_attempt", "0"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.task_kind").value("TRANSCRIPTION"));
    }

    @Test
    void questionAnsweringContextWithoutAttemptIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/ai/transcribe")
                        .file(new MockMultipartFile("audio", "voice.wav", "audio/wav", wav(HUNDRED_MILLISECOND_WAV)))
                        .file(questionContextPart(UUID.randomUUID().toString(), "null"))
                        .param("retry_attempt", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field_path").value("/context/attempt"));
    }

    /** 单题录音的 context：target=QUESTION_ANSWERING，attempt 按契约传枚举字面量。 */
    private MockMultipartFile questionContextPart(String requestId, String attemptJson) {
        String json = """
                {"request_id":"%s","input_revision":"%s","business_type":"CLASSROOM","activity_id":"ACT_1",
                 "target":"QUESTION_ANSWERING","question_id":"Q_5","attempt":%s}"""
                .formatted(requestId, revisionFor(requestId), attemptJson);
        return new MockMultipartFile("context", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private AiTask newPendingTask(AiTaskStore target, int index) {
        AiTask task = new AiTask(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), TaskKind.TRANSCRIPTION, 0, null,
                BusinessType.ASSESSMENT, "act_" + index, java.time.Instant.now());
        target.save(task);
        return task;
    }

    private String submitAndAccept(String requestId, int retryAttempt) throws Exception {
        return mockMvc.perform(submit(requestId, retryAttempt))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder submit(
            String requestId, int retryAttempt) {
        return multipart("/api/ai/transcribe")
                .file(new MockMultipartFile("audio", "voice.wav", "audio/wav", wav(HUNDRED_MILLISECOND_WAV)))
                .file(contextPart(context(requestId, null)))
                .param("retry_attempt", String.valueOf(retryAttempt));
    }

    private MockMultipartFile contextPart(com.earlylearning.early_learning_server.ai.web.TranscriptionContext context) {
        String json = """
                {"request_id":"%s","input_revision":"%s","business_type":"ASSESSMENT",
                 "activity_id":"ACT_1","target":"STORY_NARRATION"}"""
                .formatted(context.requestId(), context.inputRevision());
        return new MockMultipartFile("context", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private com.earlylearning.early_learning_server.ai.web.TranscriptionContext context(String requestId,
                                                                                       String inputRevision) {
        return new com.earlylearning.early_learning_server.ai.web.TranscriptionContext(requestId,
                inputRevision == null ? revisionFor(requestId) : inputRevision,
                BusinessType.ASSESSMENT, "ACT_1",
                com.earlylearning.early_learning_server.ai.web.TranscriptionTarget.STORY_NARRATION, null, null);
    }

    /** 同一 request_id 的重发必须带同一 input_revision，否则指纹不同、会被判成"换了输入"。 */
    private String revisionFor(String requestId) {
        return UUID.nameUUIDFromBytes(("revision:" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String taskIdOf(String responseBody) {
        return responseBody.replaceAll(".*\"task_id\":\"([^\"]+)\".*", "$1");
    }

    private AiTask awaitStage(String taskId, TaskStage expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            AiTask task = store.find(taskId);
            if (task != null && task.getStage() == expected) {
                return task;
            }
            Thread.sleep(20);
        }
        fail("任务未在预期时间内进入 " + expected + "，当前：" + store.find(taskId).getStage());
        return null;
    }

    private static byte[] wav(int dataSize) {
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize)
                .array();
    }
}
