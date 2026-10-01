package com.earlylearning.early_learning_server.ai.client.scoring;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringInput;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricConfig;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实单题评分：题目 + 回答 + 图片字节 → 模型 → 结构化分数。
 *
 * <p>用桩 ChatModel，不联网；重点是几件容易写错的事：标准由服务端注入、图片真的进了消息、
 * 版本与模型标识由服务端写（不采信模型自报）、不合格输出不能变成成功结果。
 */
class ChatModelAnswerScorerTests {

    private static final String RUBRIC_VERSION = "narrative-assessment-v1";

    private final StubChat chat = new StubChat();
    private final ChatModelAnswerScorer scorer = new ChatModelAnswerScorer(chat, rubric());

    @Test
    void scoreCarriesTheServerSideVersionNotTheModelClaim() {
        chat.content = """
                {"score":2,"max_score":2,"reason":"完整复述","evidence":[]}""";

        AnswerScoringOutput output = scorer.score(input(true), RUBRIC_VERSION);

        assertThat(output.score().score()).isEqualTo(2);
        assertThat(output.score().maxScore()).isEqualTo(2);
        assertThat(output.score().rubricVersion()).isEqualTo(RUBRIC_VERSION);
        // 模型标识取适配器实际用的那个（响应里的 model），不是模型自己说的
        assertThat(output.modelMeta().model()).isEqualTo("ecnu-plus");
        assertThat(output.modelMeta().promptVersion()).isNotBlank();
    }

    @Test
    void evidenceInTheModelOutputSurvivesParsing() {
        // 这条用例的存在理由：最初的实现把内部 record 写成了 private，
        // 于是"含证据"的响应反序列化失败、被判成模型输出不合法——而空证据的响应看不出问题。
        chat.content = """
                {"score":2,"max_score":2,"reason":"完整复述","evidence":["小狗跑过来了"]}""";

        AnswerScoringOutput output = scorer.score(input(false), RUBRIC_VERSION);

        // 模型只照抄片段；偏移由服务端定位——实测模型数不准 UTF-16 偏移，不能采信
        assertThat(output.score().evidence()).hasSize(1);
        assertThat(output.score().evidence().get(0).text()).isEqualTo("小狗跑过来了");
        assertThat(output.score().evidence().get(0).startOffset()).isEqualTo(0);
        assertThat(output.score().evidence().get(0).endOffset()).isEqualTo(6);
    }

    @Test
    void evidenceThatCannotBeFoundInTheAnswerIsDroppedNotFabricated() {
        // 模型给的片段原文里没有（改写/编造）→ 丢弃，绝不替它编证据；分数仍然保留
        chat.content = """
                {"score":2,"max_score":2,"reason":"复述了","evidence":["该儿童表达了丰富的情感","小狗跑过来了"]}""";

        AnswerScoringOutput output = scorer.score(input(false), RUBRIC_VERSION);

        assertThat(output.score().score()).isEqualTo(2);
        assertThat(output.score().evidence()).hasSize(1);
        assertThat(output.score().evidence().get(0).text()).isEqualTo("小狗跑过来了");
    }

    @Test
    void theRubricAndTheAnswerBothReachThePrompt() {
        chat.content = "{\"score\":1,\"max_score\":2,\"reason\":\"x\",\"evidence\":[]}";

        scorer.score(input(false), RUBRIC_VERSION);

        ChatModel.ChatRequest sent = chat.requests.get(0);
        String system = sent.messages().get(0).text();
        String user = sent.messages().get(1).text();
        assertThat(system).contains("QUESTION_REASONING").contains("合理的理解或推理");
        assertThat(system).contains("不得编造引文");
        assertThat(user).contains("图片里发生了什么？").contains("小狗跑过来了，它很开心。").contains("提示前的独立作答");
    }

    @Test
    void resolvedImageBytesBecomeImageParts() {
        chat.content = "{\"score\":1,\"max_score\":2,\"reason\":\"x\",\"evidence\":[]}";

        scorer.score(input(true), RUBRIC_VERSION);

        ChatModel.ChatRequest sent = chat.requests.get(0);
        assertThat(sent.hasImages()).isTrue();
        assertThat(sent.images()).hasSize(1);
        assertThat(sent.images().get(0).mimeType()).isEqualTo("image/png");
        assertThat(sent.images().get(0).content()).isEqualTo("图片字节".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void descriptionOnlyImagesDoNotBecomeImageParts() {
        chat.content = "{\"score\":1,\"max_score\":2,\"reason\":\"x\",\"evidence\":[]}";
        AnswerScoringInput input = new AnswerScoringInput("Q_5", "题目", "", Attempt.AFTER_HINT, "答案", "依据",
                List.of(new ScoringImage("CF_A", null, null, "图中是一只小狗")));

        scorer.score(input, RUBRIC_VERSION);

        assertThat(chat.requests.get(0).hasImages()).isFalse();
        assertThat(chat.requests.get(0).messages().get(1).text()).contains("随附").describedAs("只有说明时不应声称随附图片");
    }

    @Test
    void malformedModelOutputIsRejectedNotScored() {
        chat.content = "这不是 JSON";

        assertThatThrownBy(() -> scorer.score(input(false), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void aRetryableModelFailureKeepsItsCodeAndRetryability() {
        // 端口直接抛 AiTaskFailedException：失败码与可重试性原样到达执行器，评分器不再翻译一遍
        chat.failure = new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "限流", true);

        assertThatThrownBy(() -> scorer.score(input(false), RUBRIC_VERSION))
                .isInstanceOf(AiTaskFailedException.class)
                .satisfies(ex -> {
                    assertThat(((AiTaskFailedException) ex).getFailureCode()).isEqualTo(TaskFailureCode.MODEL_TIMEOUT);
                    assertThat(((AiTaskFailedException) ex).isRetryable()).isTrue();
                });
    }

    @Test
    void aPermanentModelFailureIsNotMarkedRetryable() {
        chat.failure = new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "令牌无效", false);

        assertThatThrownBy(() -> scorer.score(input(false), RUBRIC_VERSION))
                .isInstanceOf(AiTaskFailedException.class)
                .satisfies(ex -> assertThat(((AiTaskFailedException) ex).isRetryable()).isFalse());
    }

    private AnswerScoringInput input(boolean withImage) {
        return new AnswerScoringInput("Q_5", "图片里发生了什么？", "", Attempt.BEFORE_HINT,
                "小狗跑过来了，它很开心。", "故事依据",
                withImage ? List.of(new ScoringImage("CF_A", "image/png",
                        "图片字节".getBytes(StandardCharsets.UTF_8), null)) : List.of());
    }

    /** 测试用的评分标准：只保留问答条目，规则文字取自契约。 */
    private RubricConfig rubric() {
        return new RubricConfig(2, RUBRIC_VERSION, List.of(), List.of(),
                new RubricConfig.Item("统一问答推理", "理解或推理是否合理", "QUESTION_REASONING", List.of(
                        new RubricConfig.Level(2, "合理的理解或推理；"),
                        new RubricConfig.Level(1, "正确但不完整的理解或推理；"),
                        new RubricConfig.Level(0, "无回应或完全错误的推理和理解。")), null));
    }

    private class StubChat implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();
        String content = "{}";
        AiTaskFailedException failure;

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return new ChatResponse(content, "ecnu-plus");
        }

        @Override
        public String modelFor(ChatRequest request) {
            return request.hasImages() ? "ecnu-plus" : "ecnu-max";
        }
    }
}
