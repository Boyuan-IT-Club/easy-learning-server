package com.earlylearning.early_learning_server.ai.client.scoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricConfig;
import com.earlylearning.early_learning_server.ai.model.scoring.EvidenceLocator;
import com.earlylearning.early_learning_server.ai.model.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringGroup;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScore;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScoreSection;
import com.earlylearning.early_learning_server.ai.model.scoring.story.MicrostructureSection;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreContentItem;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreDimension;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 故事评分的模型适配器:把已解析的输入交给多模态模型,产出 {@code AIScore v2}。
 * 只依赖 {@link ChatModel} 端口,与厂商无关;真实实现在 client/ecnu,联调假实现在 client/fake。
 *
 * <p>数据流:故事依据 + 儿童原话 + 已解析图片 → 模型 → AIScore v2。评分标准(宏观 6 项、
 * 微观 5 项、叙事产生性、每个图片分组各自的规则)由服务端从 {@code /ai/rubric-config.json}
 * 拼进提示词,不由请求携带、不由模型自报。
 *
 * <p>与模型的分工(和 {@link ChatModelAnswerScorer} 相同):
 * <ol>
 *   <li>模型只回答「哪一项、几分、为什么、引了哪句话」;版本、满分、条目显示名、
 *       分组到规则的映射、证据偏移全部由服务端填写。</li>
 *   <li>证据只认确认文本里逐字存在的片段;偏移由服务端计算,定位不到的片段丢弃。</li>
 *   <li>任何一项缺分即抛 {@link InvalidModelOutputException},任务以
 *       {@code MODEL_OUTPUT_INVALID} 失败,不产生部分结果。</li>
 * </ol>
 *
 * <p>叙事产生性的四项量化统计({@code mean_c_unit_length} 等)恒为 null:本服务不含分词与
 * 词性工具,该条目取模型判断的 0/1/2 分。
 */
public class ChatModelStoryScorer implements StoryScorer {

    /** 提示词版本：随 prompt 一起演进，写进结果的 model_meta，便于追溯。 */
    private static final String PROMPT_VERSION = "STORY_SCORING_PROMPT_V1";

    private static final String SCHEMA_NAME = "StoryScore";
    private static final int MAX_SCORE = 2;

    /** 单项判断的形状：分数 + 理由 + 证据片段。 */
    private static final String SCORE_SHAPE = """
            {"type": "object", "additionalProperties": false,
             "properties": {
               "score": {"type": "integer", "enum": [0, 1, 2]},
               "reason": {"type": "string"},
               "evidence": {"type": "array", "items": {"type": "string"}}
             },
             "required": ["score", "reason", "evidence"]}""";

    private final ChatModel chatModel;
    private final RubricConfig rubricConfig;

    public ChatModelStoryScorer(ChatModel chatModel, RubricConfig rubricConfig) {
        this.chatModel = chatModel;
        this.rubricConfig = rubricConfig;
    }

    @Override
    public AiScore score(StoryScoringInput input, String rubricVersion) {
        RenderedPrompt rendered = render(input);
        ChatModel.ChatRequest request = new ChatModel.ChatRequest(
                List.of(new ChatModel.ChatMessage("system", systemPrompt(input)),
                        new ChatModel.ChatMessage("user", rendered.text())),
                SCHEMA_NAME, responseSchema(input), rendered.images());

        ChatModel.ChatResponse response = chatModel.complete(request);
        ModelOutput output = ModelJson.parse(response.content(), ModelOutput.class);

        return new AiScore(
                AiScore.SCHEMA_VERSION,
                rubricVersion,
                output.summary(),
                new AiScoreSection(
                        toDimensions(output.macrostructure().dimensions(), macroCodes(),
                                ChatModelStoryScorer::macroName, input.confirmedText(), "宏观"),
                        toContentItems(output.macrostructure().contentItems(), input)),
                new MicrostructureSection(
                        toDimensions(output.microstructure().dimensions(), microCodes(),
                                ChatModelStoryScorer::microName, input.confirmedText(), "微观"),
                        toProductivity(output.microstructure().productivity(), input.confirmedText())),
                // 模型标识取适配器实际用的那个，不取响应里的 self-report
                new ModelMeta(response.model(), PROMPT_VERSION));
    }

    // ---------- 提示词 ----------

    /** 评分标准逐条写进系统提示：模型看到的就是服务端这一版标准，不依赖它对标准的记忆。 */
    private String systemPrompt(StoryScoringInput input) {
        StringBuilder rules = new StringBuilder();
        rules.append("宏观结构（6 项）：\n");
        int index = 1;
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            appendItem(rules, index++, itemOf(rubricConfig.macrostructure(), code.name()));
        }
        rules.append("微观结构（5 项）：\n");
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            appendItem(rules, index++, itemOf(rubricConfig.microstructure(), code.name()));
        }
        rules.append("叙事产生性（独立一项，不属于上面的维度）：\n");
        appendItem(rules, index++, itemOf(rubricConfig.microstructure(), ProductivityStat.ITEM_CODE));

        rules.append("图片分组：每个分组按它自己的规则打分，逐组都要给：\n");
        for (ScoringGroup group : input.contentItems()) {
            rules.append("- 分组 ").append(group.contentItemId()).append(" 适用规则：");
            appendItem(rules, index++, itemOf(rubricConfig.macrostructure(), group.rubricItemCode()));
        }

        return """
                你是儿童叙事语言的评分员。请**只依据**下面的评分标准，对本次叙事逐项打 0/1/2 分并给出理由。
                每一项都要给，且**每项只出现一次**：宏观 6 项、微观 5 项、叙事产生性 1 项、每个图片分组 1 项。
                重复（同一 item_code 出现两次）或遗漏都会被判为无效输出——条目以括号里的 item_code 为准，
                显示名称可能重名（例如两条都叫「衔接使用」），**不要因为名字相同就合并或重复**。

                %s
                证据规则：
                - evidence 是儿童原话里的片段，逐字照抄，不得编造引文，不要改写、不要拼接、不要意译；
                - 不需要给出偏移，偏移由服务端计算；
                - 没有合适的原文片段就给空数组 []，不要为了凑证据而引用无关内容；
                - 不要输出条目名称、满分或版本号，这些由服务端填写。
                - 若提供了图片，请结合图片内容判断；教师给出的确认说明按"已确认的事实"对待。
                """.formatted(rules);
    }

    /**
     * 用户消息与图片在同一趟里生成：提示词里说"第 N 张图片"的编号，
     * 必须与下发给模型的图片次序严格一致，分成两处算迟早会对不上。
     */
    private RenderedPrompt render(StoryScoringInput input) {
        Map<String, ScoringImage> byFileCode = new LinkedHashMap<>();
        for (ScoringImage image : input.images()) {
            byFileCode.put(image.fileCode(), image);
        }

        StringBuilder text = new StringBuilder();
        text.append("故事依据（教师确认的标准故事）：\n").append(input.storyContext()).append("\n\n");
        text.append("儿童原话（教师已确认；空字符串表示确认无回应）：\n")
                .append(input.confirmedText() == null ? "" : input.confirmedText()).append("\n\n");
        text.append("图片分组：\n");

        List<ChatModel.ImagePart> parts = new ArrayList<>();
        for (ScoringGroup group : input.contentItems()) {
            text.append("- 分组 ").append(group.contentItemId())
                    .append("（规则 ").append(group.rubricItemCode()).append("）：");
            List<String> described = new ArrayList<>();
            int attached = 0;
            for (String fileCode : group.imageFileCodes()) {
                ScoringImage image = byFileCode.get(fileCode);
                if (image == null) {
                    // 请求校验已保证图片与分组一一对应；真出现说明校验漏了，不能装作没看见
                    throw new IllegalStateException("分组 " + group.contentItemId()
                            + " 引用的图片 " + fileCode + " 没有解析结果");
                }
                if (image.hasBytes()) {
                    parts.add(new ChatModel.ImagePart(image.mimeType(), image.content()));
                    attached++;
                } else {
                    if (image.description() == null || image.description().isBlank()) {
                        // 既没有字节也没有确认说明：这张图对模型等于不存在，不能拿 "CF_X：null" 糊过去
                        throw new IllegalStateException("分组 " + group.contentItemId() + " 的图片 "
                                + fileCode + " 既没有字节也没有确认说明，无法交给模型");
                    }
                    described.add(fileCode + "：" + image.description());
                }
            }
            if (attached > 0) {
                text.append("随附 ").append(attached).append(" 张图片（本组图片在下方整体顺序中的位置与分组顺序一致）");
            }
            for (String description : described) {
                text.append("教师确认的说明——").append(description).append("；");
            }
            text.append('\n');
        }
        text.append("\n随附图片共 ").append(parts.size()).append(" 张，按上面的分组顺序给出。");
        return new RenderedPrompt(text.toString(), List.copyOf(parts));
    }

    private static void appendItem(StringBuilder rules, int index, RubricConfig.Item item) {
        // 编号 + item_code 在前：条目身份以编号为准，显示名可能重名
        rules.append("  ").append(index).append(". ").append(item.itemCode())
                .append("（").append(item.item()).append("）\n");
        rules.append("    判定依据：").append(item.criterion()).append('\n');
        if (item.scoringInstruction() != null && !item.scoringInstruction().isBlank()) {
            // 量化类条目（如叙事产生性）没有 0/1/2 档说明，标准的指示以配置原文为准
            rules.append("    评分指示：").append(item.scoringInstruction()).append('\n');
            return;
        }
        for (RubricConfig.Level level : item.scoreLevels()) {
            rules.append("      ").append(level.score()).append(" 分：").append(level.description()).append('\n');
        }
    }

    /** 标准里必须能找到这一条；找不到说明随包发布的配置不完整，属于配置错误而不是模型问题。 */
    private static RubricConfig.Item itemOf(List<RubricConfig.Item> items, String itemCode) {
        for (RubricConfig.Item item : items) {
            if (itemCode.equals(item.itemCode())) {
                return item;
            }
        }
        throw new IllegalStateException("评分标准里缺少条目：" + itemCode);
    }

    // ---------- 响应 Schema ----------

    /**
     * 响应 Schema。
     *
     * <p><b>维度与分组用"以条目号为键的对象"而不是数组</b>：实测（真实 ECNU）数组形态下模型会把
     * 某一条给两次、漏掉另一条——随包配置里恰好有两条微观条目显示名相同（都叫「衔接使用」）。
     * 换成对象后，重复键在结构上不可能（再配严格重复键检测），漏项由 {@code required} 挡住，
     * 「集合完整且不重复」这条契约就不再靠模型自觉。
     */
    private String responseSchema(StoryScoringInput input) {
        return """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "summary": {"type": "string"},
                    "macrostructure": {
                      "type": "object",
                      "additionalProperties": false,
                      "properties": {
                        "dimensions": %s,
                        "content_items": %s
                      },
                      "required": ["dimensions", "content_items"]
                    },
                    "microstructure": {
                      "type": "object",
                      "additionalProperties": false,
                      "properties": {
                        "dimensions": %s,
                        "productivity": %s
                      },
                      "required": ["dimensions", "productivity"]
                    }
                  },
                  "required": ["summary", "macrostructure", "microstructure"]
                }""".formatted(scoreMap(macroCodes()), scoreMap(groupIds(input)), scoreMap(microCodes()),
                SCORE_SHAPE);
    }

    /** {@code {"CODE": 单项判断}} 形状，把这批条目号全部列为必填。 */
    private static String scoreMap(List<String> codes) {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (String code : codes) {
            if (!properties.isEmpty()) {
                properties.append(", ");
                required.append(", ");
            }
            properties.append('"').append(code).append("\": ").append(SCORE_SHAPE);
            required.append('"').append(code).append('"');
        }
        return """
                {"type": "object", "additionalProperties": false,
                 "properties": {%s},
                 "required": [%s]}""".formatted(properties, required);
    }

    // ---------- 模型输出 → 领域对象 ----------

    /**
     * 维度按规则顺序排好再交给校验器。
     *
     * <p>顺序是服务端的事（与条目显示名、满分同类）；Schema 已保证条目号齐全且不重复，
     * 这里再核一遍键集合，多一个少一个都判失败——不静默补、不静默丢。
     */
    private static List<ScoreDimension> toDimensions(Map<String, ModelScore> returned,
                                                     List<String> expectedCodes,
                                                     Function<String, String> nameOf,
                                                     String confirmedText,
                                                     String label) {
        requireSameCodes(returned, expectedCodes, label);
        List<ScoreDimension> ordered = new ArrayList<>(expectedCodes.size());
        for (String code : expectedCodes) {
            ModelScore item = returned.get(code);
            ordered.add(new ScoreDimension(item.score(), MAX_SCORE, item.reason(),
                    EvidenceLocator.locate(item.evidence(), confirmedText), code, nameOf.apply(code)));
        }
        return ordered;
    }

    /** 分组条目的规则映射取自请求，模型只给分与理由——它没有机会把分组配到别的规则上。 */
    private static List<ScoreContentItem> toContentItems(Map<String, ModelScore> returned, StoryScoringInput input) {
        List<String> expected = groupIds(input);
        requireSameCodes(returned, expected, "图片分组");
        List<ScoreContentItem> ordered = new ArrayList<>(expected.size());
        for (ScoringGroup group : input.contentItems()) {
            ModelScore item = returned.get(group.contentItemId());
            ordered.add(new ScoreContentItem(item.score(), MAX_SCORE, item.reason(),
                    EvidenceLocator.locate(item.evidence(), input.confirmedText()),
                    group.contentItemId(), group.rubricItemCode()));
        }
        return ordered;
    }

    private static ProductivityStat toProductivity(ModelScore returned, String confirmedText) {
        if (returned == null) {
            throw new InvalidModelOutputException("模型没有给叙事产生性打分");
        }
        // 四项量化统计留空：本服务没有分词与词性工具，模型也数不准（见类注释）
        return new ProductivityStat(null, null, null, null,
                returned.score(), MAX_SCORE, returned.reason(),
                EvidenceLocator.locate(returned.evidence(), confirmedText),
                ProductivityStat.ITEM_CODE);
    }

    private static void requireSameCodes(Map<String, ModelScore> returned, List<String> expected, String label) {
        if (returned == null) {
            throw new InvalidModelOutputException(label + "整块缺失");
        }
        for (String code : expected) {
            if (!returned.containsKey(code)) {
                throw new InvalidModelOutputException(label + "缺少条目：" + code);
            }
        }
        if (returned.size() != expected.size()) {
            throw new InvalidModelOutputException(label + "多出了规则之外的条目："
                    + returned.keySet().stream().filter(code -> !expected.contains(code)).toList());
        }
    }

    private static String macroName(String code) {
        for (MacroDimensionCode candidate : MacroDimensionCode.values()) {
            if (candidate.name().equals(code)) {
                return candidate.displayName();
            }
        }
        throw new InvalidModelOutputException("未知的宏观条目码：" + code);
    }

    private static String microName(String code) {
        for (MicroDimensionCode candidate : MicroDimensionCode.values()) {
            if (candidate.name().equals(code)) {
                return candidate.displayName();
            }
        }
        throw new InvalidModelOutputException("未知的微观条目码：" + code);
    }

    private static List<String> macroCodes() {
        List<String> codes = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            codes.add(code.name());
        }
        return codes;
    }

    private static List<String> microCodes() {
        List<String> codes = new ArrayList<>();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            codes.add(code.name());
        }
        return codes;
    }

    private static List<String> groupIds(StoryScoringInput input) {
        List<String> ids = new ArrayList<>();
        for (ScoringGroup item : input.contentItems()) {
            ids.add(item.contentItemId());
        }
        return ids;
    }

    private static String quoted(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append('"').append(value).append('"');
        }
        return joined.toString();
    }

    /** 用户消息与图片；图片次序即提示词里的编号次序。 */
    private record RenderedPrompt(String text, List<ChatModel.ImagePart> images) {
    }

    /**
     * 模型该回答的部分。
     *
     * <p><b>可见性必须是 public</b>：Jackson 反序列化 record 需要能访问规范构造器，
     * 写成 private 会在响应里带上某一类条目时抛异常（单题评分就踩过：空证据看不出问题，
     * 一有证据就解析失败）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelOutput(String summary, ModelSection macrostructure, ModelMicrostructure microstructure) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelSection(Map<String, ModelScore> dimensions,
                               @JsonProperty("content_items") Map<String, ModelScore> contentItems) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelMicrostructure(Map<String, ModelScore> dimensions, ModelScore productivity) {
    }

    /** 一项判断：分数 + 理由 + 照抄的证据片段（键是条目号或分组编号）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelScore(Integer score, String reason, List<String> evidence) {
    }
}
