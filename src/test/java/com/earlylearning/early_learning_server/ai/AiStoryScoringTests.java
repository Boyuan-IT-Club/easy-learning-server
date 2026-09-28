package com.earlylearning.early_learning_server.ai;

import java.util.List;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.adapter.fake.FakeStoryScorerConfig;
import com.earlylearning.early_learning_server.ai.score.ScoringImage;
import com.earlylearning.early_learning_server.ai.score.ScoringImageResolver;
import com.earlylearning.early_learning_server.ai.score.StoryScorer;
import com.earlylearning.early_learning_server.ai.score.StoryScoringInput;
import com.earlylearning.early_learning_server.ai.rubric.RubricProperties;
import com.earlylearning.early_learning_server.ai.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.score.AiScore;
import com.earlylearning.early_learning_server.ai.score.AiStoryScoringService;
import com.earlylearning.early_learning_server.ai.score.EvidenceValidator;
import com.earlylearning.early_learning_server.ai.score.ScoreValidator;
import com.earlylearning.early_learning_server.ai.score.StoryScoringResult;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.AiTaskProperties;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.web.AiStoryScoringController;
import com.earlylearning.early_learning_server.ai.web.ContentItem;
import com.earlylearning.early_learning_server.ai.web.ImageContext;
import com.earlylearning.early_learning_server.ai.web.ImageContextValidator;
import com.earlylearning.early_learning_server.ai.web.StoryScoringRequest;
import com.earlylearning.early_learning_server.storage.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.ObjectStorageService;
import com.earlylearning.early_learning_server.ai.score.ScoringLimits;
import com.earlylearning.early_learning_server.ai.web.StoryScoringRequestValidator;
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
import static org.mockito.Mockito.mock;

/**
 * 故事评分：版本语义、输出校验、以及请求的语义校验。
 *
 * <p>与转写一样全在内存里完成，不需要 Spring 上下文。
 */
class AiStoryScoringTests {

    private static final String RUBRIC_VERSION = "RUBRIC_2026_01";
    private static final String TEXT = "小明说他想去公园，后来又说要带小狗。";

    private final AiTaskStore store = new AiTaskStore(100);
    private final RubricService rubricService = new RubricService(new RubricProperties(RUBRIC_VERSION));
    private final ScoreValidator scoreValidator = new ScoreValidator(new EvidenceValidator());
    private final StoryScoringRequestValidator requestValidator = new StoryScoringRequestValidator(new ImageContextValidator(), new ScoringLimits(20000, 20, 5242880));
    private final FakeStoryScorerConfig fakeConfig = new FakeStoryScorerConfig();

    private volatile StoryScorer scorer = fakeConfig.fakeStoryScorer();
    private AiStoryScoringService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiTaskRunner runner = new AiTaskRunner(new AiTaskProperties(1800, 600));
        // 图片在提交路径上解析；本测试的请求都是内联图片与确认说明，不需要碰存储
        ScoringImageResolver imageResolver = new ScoringImageResolver(
                mock(CloudFileQueryService.class), mock(ObjectStorageService.class),
                new ScoringLimits(20000, 20, 5242880));
        service = new AiStoryScoringService(new AiTaskSubmission(store), runner, rubricService,
                (input, rubricVersion) -> scorer.score(input, rubricVersion),
                scoreValidator, requestValidator, imageResolver);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiStoryScoringController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void omittingVersionUsesTheServerFixedOneAndReturnsItInTheHandle() throws Exception {
        String body = mockMvc.perform(submit(request(UUID.randomUUID().toString(), null), 0))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.task_kind").value("STORY_SCORING"))
                // 契约：任务凭据必须返回**实际**采用的版本
                .andExpect(jsonPath("$.data.rubric_version").value(RUBRIC_VERSION))
                .andReturn().getResponse().getContentAsString();

        AiTask task = awaitStage(taskIdOf(body), TaskStage.SUCCEEDED);
        AiScore score = ((StoryScoringResult) task.getResult()).aiScore();
        assertThat(score.schemaVersion()).isEqualTo(2);
        assertThat(score.rubricVersion()).isEqualTo(RUBRIC_VERSION);
    }

    @Test
    void unavailableVersionIs503AndNeverSilentlySwitched() throws Exception {
        mockMvc.perform(submit(request(UUID.randomUUID().toString(), "RUBRIC_OLD_2025"), 0))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUBRIC_UNAVAILABLE"));
    }

    @Test
    void invalidModelOutputBecomesAFailedTaskNotASuccessfulScore() throws Exception {
        // 用一个"少一个维度"的输出，模拟模型返回不合格结果
        scorer = (input, rubricVersion) -> dropOneDimension(
                fakeConfig.fakeStoryScorer().score(input, rubricVersion));

        String taskId = taskIdOf(mockMvc.perform(submit(request(UUID.randomUUID().toString(), null), 0))
                .andReturn().getResponse().getContentAsString());

        AiTask task = awaitStage(taskId, TaskStage.FAILED);
        assertThat(task.getFailure().code()).isEqualTo(TaskFailureCode.MODEL_OUTPUT_INVALID);
        assertThat(task.getResult()).isNull();
    }

    @Test
    void retryContinuesWithTheVersionRecordedOnTheTask() throws Exception {
        String requestId = UUID.randomUUID().toString();
        // 首次调用失败（可重试）
        scorer = (input, rubricVersion) -> {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "临时故障", true, null);
        };
        String taskId = taskIdOf(mockMvc.perform(submit(request(requestId, null), 0))
                .andReturn().getResponse().getContentAsString());
        awaitStage(taskId, TaskStage.FAILED);

        // 重试时不带版本：仍应沿用任务记录的版本
        scorer = fakeConfig.fakeStoryScorer();
        String restarted = taskIdOf(mockMvc.perform(submit(request(requestId, null), 1))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());

        assertThat(restarted).isEqualTo(taskId);
        AiTask task = awaitStage(taskId, TaskStage.SUCCEEDED);
        assertThat(task.getAttemptNo()).isEqualTo(1);
        assertThat(((StoryScoringResult) task.getResult()).aiScore().rubricVersion()).isEqualTo(RUBRIC_VERSION);
    }

    @Test
    void retryWithADifferentVersionIsRejectedInsteadOfSwitching() throws Exception {
        String requestId = UUID.randomUUID().toString();
        scorer = (input, rubricVersion) -> {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "临时故障", true, null);
        };
        String taskId = taskIdOf(mockMvc.perform(submit(request(requestId, null), 0))
                .andReturn().getResponse().getContentAsString());
        awaitStage(taskId, TaskStage.FAILED);

        // 任务记录的是 RUBRIC_2026_01；请求却报别的版本 → 按不可用拒绝，不悄悄换版
        assertThatThrownBy(() -> service.submit(request(requestId, "RUBRIC_2025_12"), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RUBRIC_UNAVAILABLE);
    }

    @Test
    void unconfirmedTextIsRejected() {
        StoryScoringRequest unconfirmed = new StoryScoringRequest(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), BusinessType.ASSESSMENT, "ACT_1", null,
                TEXT, false, "故事依据", groups(), images());

        assertThatThrownBy(() -> requestValidator.validate(unconfirmed))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/text_confirmed"));
    }

    @Test
    void imagesMustCoverEveryReferencedPicture() {
        // 分组引用了两张图，images 却只给了一张
        StoryScoringRequest incomplete = new StoryScoringRequest(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), BusinessType.ASSESSMENT, "ACT_1", null,
                TEXT, true, "故事依据", groups(), List.of(images().get(0)));

        assertThatThrownBy(() -> requestValidator.validate(incomplete))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/images"));
    }

    @Test
    void aFileCodeOnlyImageIsAcceptedBecauseTheContractAllowsIt() {
        // 契约：故事评分的 images「由服务端按 file_code 取图（SERVER_FETCH），或教师确认过的图片说明……
        // 不再要求客户端内联图片内容」。取回发生在提交的同步路径上（ScoringImageResolver），
        // 取回本身由 ScoringImageResolverTests 覆盖；这里只钉住请求校验不再拒它。
        StoryScoringRequest fileCodeOnly = new StoryScoringRequest(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), BusinessType.ASSESSMENT, "ACT_1", null,
                TEXT, true, "故事依据", groups(),
                List.of(new ImageContext(ImageContext.ImageKind.SERVER_FETCH, "CF_A", null, null, null, null),
                        new ImageContext(ImageContext.ImageKind.CONFIRMED_DESCRIPTION, "CF_B", null, null, "图2是一只小猫", true)));

        requestValidator.validate(fileCodeOnly);
    }

    @Test
    void theShippedFakeProducesAContractValidScore() {
        // 假实现本身也要经得起校验，否则联调时会被自己的校验器挡住
        assertThat(scorer.score(input(request(UUID.randomUUID().toString(), null)), RUBRIC_VERSION))
                .satisfies(score -> scoreValidator.validate(score, RUBRIC_VERSION, TEXT, groups()));
    }

    /** 与 {@code images()} 对应的已解析图片：内联的那张解出 3 字节，说明的那张只带文字。 */
    private StoryScoringInput input(StoryScoringRequest request) {
        return new StoryScoringInput(request.confirmedText(), request.storyContext(), request.contentItems(),
                List.of(new ScoringImage("CF_A", "image/png", new byte[] {0, 0, 0}, null),
                        new ScoringImage("CF_B", null, null, "图片已确认")));
    }

    private AiScore dropOneDimension(AiScore score) {
        var short6 = new java.util.ArrayList<>(score.macrostructure().dimensions());
        short6.remove(0);
        return new AiScore(score.schemaVersion(), score.rubricVersion(), score.summary(),
                new com.earlylearning.early_learning_server.ai.score.AiScoreSection(
                        short6, score.macrostructure().contentItems()),
                score.microstructure(), score.modelMeta());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(
            StoryScoringRequest request, int retryAttempt) {
        return post("/api/ai/score")
                .param("retry_attempt", String.valueOf(retryAttempt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request));
    }

    /** 手写 JSON：请求体的字段名要与契约一致，这里不依赖容器的 ObjectMapper。 */
    private String toJson(StoryScoringRequest request) {
        String groups = """
                [{"content_item_id":"GROUP_1","image_file_codes":["CF_A"],"rubric_item_code":"IMG_ITEM_1"},
                 {"content_item_id":"GROUP_2","image_file_codes":["CF_B"],"rubric_item_code":"IMG_ITEM_2"}]""";
        String images = """
                [{"kind":"INLINE_IMAGE","file_code":"CF_A","mime_type":"image/png","content_base64":"AAAA"},
                 {"kind":"CONFIRMED_DESCRIPTION","file_code":"CF_B","description":"图片已确认","confirmed":true}]""";
        return """
                {"request_id":"%s","input_revision":"%s","business_type":"ASSESSMENT","activity_id":"ACT_1",
                 "rubric_version":%s,"confirmed_text":"%s","text_confirmed":true,
                 "story_context":"故事依据","content_items":%s,"images":%s}"""
                .formatted(request.requestId(), request.inputRevision(),
                        request.rubricVersion() == null ? "null" : "\"" + request.rubricVersion() + "\"",
                        TEXT, groups, images);
    }

    private StoryScoringRequest request(String requestId, String rubricVersion) {
        return new StoryScoringRequest(requestId, revisionFor(requestId), BusinessType.ASSESSMENT,
                "ACT_1", rubricVersion, TEXT, true, "故事依据", groups(), images());
    }

    /** 同一 request_id 的重发必须带同一 input_revision，否则指纹不同、会被判成"换了输入"。 */
    private String revisionFor(String requestId) {
        return UUID.nameUUIDFromBytes(("revision:" + requestId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    private List<ContentItem> groups() {
        return List.of(new ContentItem("GROUP_1", List.of("CF_A"), "IMG_ITEM_1"),
                new ContentItem("GROUP_2", List.of("CF_B"), "IMG_ITEM_2"));
    }

    private List<ImageContext> images() {
        return List.of(new ImageContext(ImageContext.ImageKind.INLINE_IMAGE, "CF_A", "image/png", "AAAA", null, null),
                new ImageContext(ImageContext.ImageKind.CONFIRMED_DESCRIPTION, "CF_B", null, null, "图片已确认", true));
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
