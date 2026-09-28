package com.earlylearning.early_learning_server.ai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.adapter.fake.FakeAnswerScorerConfig;
import com.earlylearning.early_learning_server.ai.score.AnswerScorer;
import com.earlylearning.early_learning_server.ai.rubric.RubricProperties;
import com.earlylearning.early_learning_server.ai.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.score.AiAnswerScoringService;
import com.earlylearning.early_learning_server.ai.score.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.score.AnswerScoringResult;
import com.earlylearning.early_learning_server.ai.score.EvidenceValidator;
import com.earlylearning.early_learning_server.ai.score.ModelMeta;
import com.earlylearning.early_learning_server.ai.score.QuestionAiScore;
import com.earlylearning.early_learning_server.ai.score.QuestionScoreValidator;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.Attempt;
import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.web.AiAnswerScoringController;
import com.earlylearning.early_learning_server.ai.web.AnswerScoringRequest;
import com.earlylearning.early_learning_server.ai.web.AnswerScoringRequestValidator;
import com.earlylearning.early_learning_server.ai.web.ImageContext;
import com.earlylearning.early_learning_server.ai.web.ImageContextValidator;
import com.earlylearning.early_learning_server.ai.web.ScoringQuestion;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 单题评分：一次提交只评一次作答，两次作答互不覆盖。
 *
 * <p>全在内存里完成，不需要 Spring 上下文。
 */
class AnswerScoringTests {

    private static final String RUBRIC_VERSION = "RUBRIC_2026_01";
    private static final String TEXT = "它是一只小狗，趴在门口等主人回来。";
    private static final String QUESTION_ID = "Q_5";

    private final AiTaskStore store = new AiTaskStore(100);
    private final RubricService rubricService = new RubricService(new RubricProperties(RUBRIC_VERSION));
    private final QuestionScoreValidator scoreValidator =
            new QuestionScoreValidator(new EvidenceValidator());
    private final AnswerScoringRequestValidator requestValidator =
            new AnswerScoringRequestValidator(new ImageContextValidator());
    private final FakeAnswerScorerConfig fakeConfig = new FakeAnswerScorerConfig();

    private volatile AnswerScorer scorer = fakeConfig.fakeAnswerScorer();
    private AiAnswerScoringService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiTaskRunner runner = new AiTaskRunner(1800, 600);
        service = new AiAnswerScoringService(new AiTaskSubmission(store), runner, rubricService,
                (request, rubricVersion) -> scorer.score(request, rubricVersion),
                scoreValidator, requestValidator);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiAnswerScoringController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void omittingVersionUsesTheServerFixedOneAndResultCarriesQuestionAndAttempt() throws Exception {
        String body = mockMvc.perform(submit(request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null), 0))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.task_kind").value("ANSWER_SCORING"))
                .andExpect(jsonPath("$.data.rubric_version").value(RUBRIC_VERSION))
                .andReturn().getResponse().getContentAsString();

        AiTask task = awaitStage(taskIdOf(body), TaskStage.SUCCEEDED);
        AnswerScoringResult result = (AnswerScoringResult) task.getResult();

        assertThat(result.questionId()).isEqualTo(QUESTION_ID);
        assertThat(result.attempt()).isEqualTo(Attempt.BEFORE_HINT);
        assertThat(result.aiScore().score()).isBetween(0, 2);
        assertThat(result.aiScore().maxScore()).isEqualTo(2);
        assertThat(result.aiScore().rubricVersion()).isEqualTo(RUBRIC_VERSION);
        // 契约把模型元数据放在任务结果外层，不在 ai_score 里
        assertThat(result.modelMeta()).isNotNull();
    }

    @Test
    void beforeAndAfterHintAreIndependentTasksWhoseScoresDoNotOverwriteEachOther() throws Exception {
        String beforeRequestId = UUID.randomUUID().toString();
        String afterRequestId = UUID.randomUUID().toString();
        scorer = (request, rubricVersion) -> new AnswerScoringOutput(
                new QuestionAiScore(rubricVersion, request.attempt() == Attempt.AFTER_HINT ? 2 : 1, 2,
                        "示例理由", List.of()),
                new ModelMeta("m", "PROMPT_V1"));

        // 故意颠倒顺序：先提交提示后，再提交提示前
        String afterBody = mockMvc.perform(submit(request(afterRequestId, Attempt.AFTER_HINT, null), 0))
                .andReturn().getResponse().getContentAsString();
        String beforeBody = mockMvc.perform(submit(request(beforeRequestId, Attempt.BEFORE_HINT, null), 0))
                .andReturn().getResponse().getContentAsString();

        String afterTaskId = taskIdOf(afterBody);
        String beforeTaskId = taskIdOf(beforeBody);
        assertThat(beforeTaskId).isNotEqualTo(afterTaskId);

        AnswerScoringResult after = (AnswerScoringResult) awaitStage(afterTaskId, TaskStage.SUCCEEDED).getResult();
        AnswerScoringResult before = (AnswerScoringResult) awaitStage(beforeTaskId, TaskStage.SUCCEEDED).getResult();

        // 后提交的那份没有覆盖先提交的：两份都在，各自带自己的 attempt 与分数
        assertThat(before.attempt()).isEqualTo(Attempt.BEFORE_HINT);
        assertThat(before.aiScore().score()).isEqualTo(1);
        assertThat(after.attempt()).isEqualTo(Attempt.AFTER_HINT);
        assertThat(after.aiScore().score()).isEqualTo(2);
    }

    @Test
    void missingScoreBecomesAFailedTaskInsteadOfASuccessfulZero() throws Exception {
        // 契约：null 不能作为成功结果
        scorer = (request, rubricVersion) ->
                new AnswerScoringOutput(new QuestionAiScore(rubricVersion, null, 2, "示例理由", List.of()),
                        new ModelMeta("m", "PROMPT_V1"));

        String taskId = taskIdOf(mockMvc.perform(submit(request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null), 0))
                .andReturn().getResponse().getContentAsString());

        AiTask task = awaitStage(taskId, TaskStage.FAILED);
        assertThat(task.getFailure().code()).isEqualTo(TaskFailureCode.MODEL_OUTPUT_INVALID);
        assertThat(task.getResult()).isNull();
    }

    @Test
    void retryContinuesWithTheVersionRecordedOnTheTask() throws Exception {
        String requestId = UUID.randomUUID().toString();
        scorer = (request, rubricVersion) -> {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "临时故障", true, null);
        };
        String taskId = taskIdOf(mockMvc.perform(submit(request(requestId, Attempt.AFTER_HINT, null), 0))
                .andReturn().getResponse().getContentAsString());
        awaitStage(taskId, TaskStage.FAILED);

        scorer = fakeConfig.fakeAnswerScorer();
        String restarted = taskIdOf(mockMvc.perform(submit(request(requestId, Attempt.AFTER_HINT, null), 1))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());

        assertThat(restarted).isEqualTo(taskId);
        AiTask task = awaitStage(taskId, TaskStage.SUCCEEDED);
        assertThat(task.getAttemptNo()).isEqualTo(1);
        assertThat(((AnswerScoringResult) task.getResult()).aiScore().rubricVersion()).isEqualTo(RUBRIC_VERSION);
    }

    @Test
    void retryWithADifferentVersionIsRejectedInsteadOfSwitching() throws Exception {
        String requestId = UUID.randomUUID().toString();
        scorer = (request, rubricVersion) -> {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "临时故障", true, null);
        };
        String taskId = taskIdOf(mockMvc.perform(submit(request(requestId, Attempt.AFTER_HINT, null), 0))
                .andReturn().getResponse().getContentAsString());
        awaitStage(taskId, TaskStage.FAILED);

        assertThatThrownBy(() -> service.submit(request(requestId, Attempt.AFTER_HINT, "RUBRIC_2025_12"), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RUBRIC_UNAVAILABLE);
    }

    @Test
    void switchingAttemptUnderTheSameRequestIdIsTreatedAsChangedInput() throws Exception {
        // 同一 request_id 换了 attempt：是换了输入，不是重发
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(submit(request(requestId, Attempt.BEFORE_HINT, null), 0)).andExpect(status().isAccepted());

        assertThatThrownBy(() -> service.submit(request(requestId, Attempt.AFTER_HINT, null), 0))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void unconfirmedTextIsRejected() {
        AnswerScoringRequest unconfirmed = new AnswerScoringRequest(UUID.randomUUID().toString(),
                revisionFor("x"), BusinessType.ASSESSMENT, "ACT_1", null, TEXT, false, "故事依据",
                new ScoringQuestion(QUESTION_ID, "问题原文", ""), Attempt.BEFORE_HINT, List.of());

        assertThatThrownBy(() -> requestValidator.validate(unconfirmed))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/text_confirmed"));
    }

    @Test
    void emptyHintIsAcceptedButMissingHintFieldIsRejected() throws Exception {
        // 预设提示允许为空字符串（这道题不设提示）
        mockMvc.perform(submit(request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null), 0))
                .andExpect(status().isAccepted());

        // 但契约把它列为必填：整个字段缺失是 400
        mockMvc.perform(postJson(bodyWithoutHint()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void imagesKeyMustBePresentEvenThoughAnEmptyArrayIsFine() {
        // 契约 required 含 images：空数组合法，整键缺失不行
        AnswerScoringRequest missingImages = new AnswerScoringRequest(UUID.randomUUID().toString(),
                revisionFor("y"), BusinessType.ASSESSMENT, "ACT_1", null, TEXT, true, "故事依据",
                new ScoringQuestion(QUESTION_ID, "问题原文", ""), Attempt.BEFORE_HINT, null);

        assertThatThrownBy(() -> requestValidator.validate(missingImages))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/images"));
    }

    @Test
    void imagesMayBeEmptyButAMalformedOneIsRejected() throws Exception {
        // 纯文本故事依据足够时可以不给图片
        mockMvc.perform(submit(request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null), 0))
                .andExpect(status().isAccepted());

        AnswerScoringRequest withBrokenImage = request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null,
                List.of(new ImageContext(ImageContext.ImageKind.INLINE_IMAGE, "CF_A", "image/png", null, null, null)));

        assertThatThrownBy(() -> requestValidator.validate(withBrokenImage))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/images/0/content_base64"));
    }

    @Test
    void theShippedFakeProducesAContractValidScore() {
        assertThat(scorer.score(request(UUID.randomUUID().toString(), Attempt.BEFORE_HINT, null), RUBRIC_VERSION))
                .satisfies(output -> scoreValidator.validate(output, RUBRIC_VERSION, TEXT));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(
            AnswerScoringRequest request, int retryAttempt) {
        return postJson(toJson(request)).param("retry_attempt", String.valueOf(retryAttempt));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String json) {
        return post("/api/ai/score-answer").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String toJson(AnswerScoringRequest request) {
        String hint = request.question().hint() == null ? "null" : "\"" + request.question().hint() + "\"";
        return """
                {"request_id":"%s","input_revision":"%s","business_type":"%s","activity_id":"%s",
                 "rubric_version":%s,"confirmed_text":"%s","text_confirmed":true,"story_context":"%s",
                 "question":{"question_id":"%s","text":"问题原文","hint":%s},
                 "attempt":"%s","images":[]}"""
                .formatted(request.requestId(), request.inputRevision(), request.businessType(),
                        request.activityId(),
                        request.rubricVersion() == null ? "null" : "\"" + request.rubricVersion() + "\"",
                        TEXT, "故事依据", QUESTION_ID, hint, request.attempt());
    }

    /** 缺少 question.hint 字段的请求体，用来验证它是必填。 */
    private String bodyWithoutHint() {
        return """
                {"request_id":"%s","input_revision":"%s","business_type":"ASSESSMENT","activity_id":"ACT_1",
                 "confirmed_text":"%s","text_confirmed":true,"story_context":"故事依据",
                 "question":{"question_id":"%s","text":"问题原文"},"attempt":"BEFORE_HINT","images":[]}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), TEXT, QUESTION_ID);
    }

    private AnswerScoringRequest request(String requestId, Attempt attempt, String rubricVersion) {
        return request(requestId, attempt, rubricVersion, List.of());
    }

    private AnswerScoringRequest request(String requestId, Attempt attempt, String rubricVersion,
                                         List<ImageContext> images) {
        return new AnswerScoringRequest(requestId, revisionFor(requestId), BusinessType.ASSESSMENT, "ACT_1",
                rubricVersion, TEXT, true, "故事依据",
                new ScoringQuestion(QUESTION_ID, "问题原文", ""), attempt, images);
    }

    /** 同一 request_id 的重发必须带同一 input_revision，否则指纹不同、会被判成"换了输入"。 */
    private String revisionFor(String requestId) {
        return UUID.nameUUIDFromBytes(("revision:" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String taskIdOf(String body) {
        return body.replaceAll(".*\"task_id\":\"([^\"]+)\".*", "$1");
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
        fail("任务未在预期时间内进入 " + expected);
        return null;
    }
}
