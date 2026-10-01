package com.earlylearning.early_learning_server.ai.client.scoring;
import com.earlylearning.early_learning_server.ai.model.scoring.EvidenceLocator;
import com.earlylearning.early_learning_server.ai.model.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringInput;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.model.scoring.question.QuestionAiScore;
import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.earlylearning.early_learning_server.ai.service.scoring.ScoringImageResolver;

import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricConfig;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单题评分的模型适配器:把题目、预设提示、本次回答与已解析图片交给多模态模型,产出结构化分数。
 * 只依赖 {@link ChatModel} 端口,与厂商无关;图片字节由服务端按 file_code 取回(见 {@code ScoringImageResolver})。
 *
 * <p>分工:
 * <ul>
 *   <li>评分标准由服务端从 {@code RUBRIC_CONFIG} 的 {@code question_reasoning} 条目注入提示词,
 *       不由请求携带、不由模型自报;</li>
 *   <li>模型只给分与理由;{@code rubric_version}、提示词版本、模型标识由服务端写入结果。</li>
 * </ul>
 */
public class ChatModelAnswerScorer implements AnswerScorer {


    /** 提示词版本:随 prompt 演进,写进结果的 model_meta,便于追溯。 */
    private static final String PROMPT_VERSION = "QUESTION_SCORING_PROMPT_V1";

    /** 只声明模型该回答的字段:版本与模型标识由服务端补。 */
    private static final String RESPONSE_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "score": {"type": "integer", "enum": [0, 1, 2]},
                "max_score": {"type": "integer", "enum": [2]},
                "reason": {"type": "string"},
                "evidence": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": ["score", "max_score", "reason", "evidence"]
            }
            """;

    private final ChatModel chat;
    private final RubricConfig rubric;

    public ChatModelAnswerScorer(ChatModel chat, RubricConfig rubric) {
        this.chat = chat;
        this.rubric = rubric;
    }

    @Override
    public AnswerScoringOutput score(AnswerScoringInput input, String rubricVersion) {
        ChatModel.ChatRequest request = new ChatModel.ChatRequest(
                List.of(new ChatModel.ChatMessage("system", systemPrompt()),
                        new ChatModel.ChatMessage("user", userPrompt(input))),
                "QuestionScore", RESPONSE_SCHEMA, imageParts(input));

        // 端口失败已经是 AiTaskFailedException（带失败码与可重试性），执行器认得，这里不必再翻译一次
        ChatModel.ChatResponse response = chat.complete(request);

        ModelOutput output = ModelJson.parse(response.content(), ModelOutput.class);
        QuestionAiScore score = new QuestionAiScore(rubricVersion, output.score(), output.maxScore(),
                output.reason(), EvidenceLocator.locate(output.evidence(), input.confirmedText()));
        // 模型标识取适配器实际用的那个，不取响应里的 self-report
        return new AnswerScoringOutput(score, new ModelMeta(response.model(), PROMPT_VERSION));
    }

    /** 评分标准由服务端注入；证据规则原文（UTF-16 偏移、左闭右开、不得编造）。 */
    private String systemPrompt() {
        RubricConfig.Item item = rubric.questionReasoning();
        StringBuilder rules = new StringBuilder();
        if (item != null) {
            rules.append("评分条目：").append(item.item()).append("（").append(item.itemCode()).append("）\n");
            rules.append("判定依据：").append(item.criterion()).append('\n');
            for (RubricConfig.Level level : item.scoreLevels()) {
                rules.append("  ").append(level.score()).append(" 分：").append(level.description()).append('\n');
            }
        }
        return """
                你是儿童语言评估的评分员。请**只依据**下面的评分标准给一次回答打 0/1/2 分，并给出理由。

                %s
                证据规则：
                - evidence 是回答原文里的片段，逐字照抄，不得编造引文，不要改写、不要拼接、不要意译；
                - **不需要给出偏移**，偏移由服务端计算（实测模型数不准 UTF-16 偏移，交给服务端才可靠）；
                - 没有合适的原文片段就给空数组 []，不要为了凑证据而引用无关内容。
                - 若提供了图片，请结合图片内容判断回答是否切题。
                """.formatted(rules);
    }

    private String userPrompt(AnswerScoringInput input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("题目：").append(input.questionText()).append('\n');
        if (input.hint() != null && !input.hint().isBlank()) {
            prompt.append("题目预设提示：").append(input.hint()).append('\n');
        }
        prompt.append("本次是：").append(input.attempt() == com.earlylearning.early_learning_server.ai.model.task.Attempt.BEFORE_HINT
                ? "提示前的独立作答" : "给出提示后的作答").append('\n');
        if (input.storyContext() != null && !input.storyContext().isBlank()) {
            prompt.append("故事依据：").append(input.storyContext()).append('\n');
        }
        prompt.append("本次回答（原文，空表示确认无回应）：").append(input.confirmedText()).append('\n');
        if (input.hasImages()) {
            prompt.append("随附 ").append(input.images().size()).append(" 张与本次问答相关的图片。\n");
        }
        return prompt.toString();
    }

    private List<ChatModel.ImagePart> imageParts(AnswerScoringInput input) {
        if (!input.hasImages()) {
            return List.of();
        }
        List<ChatModel.ImagePart> parts = new ArrayList<>();
        for (ScoringImage image : input.images()) {
            if (image.hasBytes()) {
                parts.add(new ChatModel.ImagePart(image.mimeType(), image.content()));
            }
        }
        return parts;
    }


    /** 模型该回答的部分；版本与服务端标识不在这里。 */
    /**
     * 模型该回答的部分。
     *
     * <p><b>可见性必须是 public</b>：Jackson 反序列化 record 需要能访问规范构造器，
     * 写成 private 会在改成有证据的响应时抛异常——而空证据的响应恰好不会触发，
     * 于是这种错很难在单测里被发现（本类最初就踩了这个坑）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelOutput(Integer score,
                              @JsonProperty("max_score") Integer maxScore,
                              String reason,
                              List<String> evidence) {
    }
}
