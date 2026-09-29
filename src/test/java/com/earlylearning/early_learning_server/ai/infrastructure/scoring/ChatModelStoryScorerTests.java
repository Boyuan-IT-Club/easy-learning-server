package com.earlylearning.early_learning_server.ai.infrastructure.scoring;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.StoryScoringInput;
import com.earlylearning.early_learning_server.ai.domain.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.ScoreDimension;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.ScoreContentItem;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.ai.domain.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.AiScore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.domain.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.domain.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.domain.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricConfig;
import com.earlylearning.early_learning_server.ai.domain.scoring.ScoringGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实故事评分：故事依据 + 儿童原话 + 图片 → 模型 → {@code AIScore v2}。
 *
 * <p>用桩 ChatModel，不联网。重点钉住几件容易写错的事：服务端标准逐条进了提示词、
 * 图片按分组顺序下发（说明类只进文字）、结构字段由服务端填（模型没机会写错）、
 * 缺项与非法输出都判失败而不是给出一个"看起来成功"的分数。
 */
class ChatModelStoryScorerTests {

    private static final String RUBRIC_VERSION = "narrative-assessment-v1";
    private static final String GROUP_1 = "GROUP_1";
    private static final String GROUP_2 = "GROUP_2";
    private static final String GROUP_RULE = "NARRATIVE_CONTENT_01";
    private static final String TEXT = "小狗跑过来了，它很开心。";

    private final StubChat chat = new StubChat();
    private final ChatModelStoryScorer scorer = new ChatModelStoryScorer(chat, rubric());

    @Test
    void theServerSideStandardReachesThePromptItemByItem() {
        chat.content = validOutput(TEXT);
        scorer.score(input(), RUBRIC_VERSION);

        String system = chat.requests.get(0).messages().get(0).text();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            assertThat(system).contains(code.name()).contains("判定依据-" + code.name())
                    .contains("2 分说明-" + code.name());
        }
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            assertThat(system).contains(code.name()).contains("判定依据-" + code.name());
        }
        // 量化类条目没有 0/1/2 档，标准以配置原文的"评分指示"下发
        assertThat(system).contains(ProductivityStat.ITEM_CODE).contains("不预设各等级的固定阈值");
        // 每个图片分组都要带上它适用的规则
        assertThat(system).contains(GROUP_1).contains(GROUP_2).contains(GROUP_RULE);
    }

    @Test
    void imagesAreAttachedInGroupOrderAndConfirmedDescriptionsStayText() {
        chat.content = validOutput(TEXT);
        scorer.score(input(), RUBRIC_VERSION);

        ChatModel.ChatRequest request = chat.requests.get(0);
        // CF_A 是真图（进 ImagePart），CF_B 只有确认说明（只进文字）
        assertThat(request.images()).hasSize(1);
        assertThat(request.images().get(0).content()).isEqualTo("图片字节".getBytes(StandardCharsets.UTF_8));
        String user = request.messages().get(1).text();
        assertThat(user).contains("故事依据").contains(TEXT).contains("教师确认的说明");
        assertThat(user).contains("CF_B");
    }

    @Test
    void structureFieldsAreFilledByTheServerNotByTheModel() {
        chat.content = validOutput(TEXT);

        AiScore score = scorer.score(input(), RUBRIC_VERSION);

        assertThat(score.schemaVersion()).isEqualTo(AiScore.SCHEMA_VERSION);
        assertThat(score.rubricVersion()).isEqualTo(RUBRIC_VERSION);
        assertThat(score.macrostructure().dimensions()).hasSize(MacroDimensionCode.values().length);
        for (ScoreDimension dimension : score.macrostructure().dimensions()) {
            assertThat(dimension.maxScore()).isEqualTo(2);
            // 显示名由服务端按条目码查枚举，不采信模型
            assertThat(dimension.itemName())
                    .isEqualTo(MacroDimensionCode.valueOf(dimension.itemCode()).displayName());
        }
        // 分组到规则的映射取自请求：模型连这个字段都没有机会填
        assertThat(score.macrostructure().contentItems()).extracting(ScoreContentItem::rubricItemCode)
                .containsOnly(GROUP_RULE);
    }

    @Test
    void dimensionsComeBackInRuleOrderEvenWhenTheModelShufflesThem() {
        // 模型按自己的顺序给（这里倒着来），结果必须回到规则顺序，否则校验器会拒
        StringBuilder dims = new StringBuilder();
        List<MacroDimensionCode> reversed = new ArrayList<>(List.of(MacroDimensionCode.values()));
        java.util.Collections.reverse(reversed);
        for (MacroDimensionCode code : reversed) {
            dims.append(scoreEntry(code.name(), TEXT)).append(',');
        }
        chat.content = output(dims.toString(), microDimensions(TEXT), contentItems(TEXT, GROUP_1 + ", " + GROUP_2));

        AiScore score = scorer.score(input(), RUBRIC_VERSION);

        assertThat(score.macrostructure().dimensions()).extracting(ScoreDimension::itemCode)
                .containsExactly("EVENT_SEQUENCE", "PLOT_STRUCTURE", "THEME", "COHERENCE", "CAUSAL_LOGIC",
                        "DETAIL_EXPANSION");
    }

    @Test
    void evidenceIsLocatedOnTheServerAndFabricatedQuotesAreDropped() {
        chat.content = output(macroDimensions("小狗跑过来了"), microDimensionsExcluding("这句话原文里没有"),
                contentItems("是小狗跑过来了", GROUP_1 + ", " + GROUP_2));

        AiScore score = scorer.score(input(), RUBRIC_VERSION);

        ScoreDimension first = score.macrostructure().dimensions().get(0);
        assertThat(first.evidence()).hasSize(1);
        assertThat(first.evidence().get(0).text()).isEqualTo("小狗跑过来了");
        assertThat(first.evidence().get(0).startOffset()).isEqualTo(0);
        assertThat(first.evidence().get(0).endOffset()).isEqualTo(6);
        // 原文里找不到的引文一律丢弃，绝不替模型编证据
        assertThat(score.microstructure().dimensions().get(0).evidence()).isEmpty();
    }

    @Test
    void productivityKeepsTheJudgementButLeavesQuantitativeStatsEmpty() {
        chat.content = validOutput(TEXT);

        ProductivityStat productivity = scorer.score(input(), RUBRIC_VERSION).microstructure().productivity();

        assertThat(productivity.itemCode()).isEqualTo(ProductivityStat.ITEM_CODE);
        assertThat(productivity.score()).isEqualTo(2);
        assertThat(productivity.maxScore()).isEqualTo(2);
        assertThat(productivity.reason()).isNotBlank();
        // 没有分词与词性工具，这几项统一留空——不让模型去数它数不准的东西
        assertThat(productivity.meanCUnitLength()).isNull();
        assertThat(productivity.adjectiveCount()).isNull();
        assertThat(productivity.adverbCount()).isNull();
        assertThat(productivity.conjunctionCount()).isNull();
    }

    @Test
    void aMissingItemFailsInsteadOfScoring() {
        // 少一个宏观条目：不能静默补，也不能返回一个"看起来成功"的分数
        List<MacroDimensionCode> codes = new ArrayList<>(List.of(MacroDimensionCode.values()));
        codes.remove(0);
        StringBuilder dims = new StringBuilder();
        for (MacroDimensionCode code : codes) {
            dims.append(scoreEntry(code.name(), TEXT)).append(',');
        }
        chat.content = output(dims.toString(), microDimensions(TEXT), contentItems(TEXT, GROUP_1 + ", " + GROUP_2));

        assertThatThrownBy(() -> scorer.score(input(), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void aDuplicatedItemCodeFailsInsteadOfBeingScored() {
        // 端到端实测抓到的真实形态：随包配置里两条微观条目显示名相同（都叫「衔接使用」），
        // 真实模型把 CONJUNCTION_COHESION 给了两次、漏掉 REFERENTIAL_COHESION。
        // 这种输出必须判失败——「维度集合完整、顺序正确、不重复」。
        String micro = microDimensions(TEXT)
                .replace("\"REFERENTIAL_COHESION\":", "\"CONJUNCTION_COHESION\":");
        chat.content = output(macroDimensions(TEXT), micro, contentItems(TEXT, GROUP_1 + ", " + GROUP_2));

        assertThatThrownBy(() -> scorer.score(input(), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void anItemCodeOutsideTheRulesFailsInsteadOfBeingScored() {
        // Schema 写的是 additionalProperties:false，但端点并不保证执行——多出来的条目必须在这一层挡住，
        // 否则它会被当成一条"规则之外的维度"混进结果
        chat.content = output(macroDimensions(TEXT) + scoreEntry("NOT_A_RULE", TEXT) + ",",
                microDimensions(TEXT), contentItems(TEXT, GROUP_1 + ", " + GROUP_2));

        assertThatThrownBy(() -> scorer.score(input(), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void aGroupTheModelDidNotScoreFailsInsteadOfScoring() {
        chat.content = output(macroDimensions(TEXT), microDimensions(TEXT), contentItems(TEXT, GROUP_1));

        assertThatThrownBy(() -> scorer.score(input(), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void malformedModelOutputIsRejectedNotScored() {
        chat.content = "这不是 JSON";

        assertThatThrownBy(() -> scorer.score(input(), RUBRIC_VERSION))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    // ---------- 夹具 ----------

    private StoryScoringInput input() {
        return new StoryScoringInput(TEXT, "故事依据：小狗与主人",
                List.of(new ScoringGroup(GROUP_1, List.of("CF_A"), GROUP_RULE),
                        new ScoringGroup(GROUP_2, List.of("CF_B"), GROUP_RULE)),
                List.of(new ScoringImage("CF_A", "image/png", "图片字节".getBytes(StandardCharsets.UTF_8), null),
                        new ScoringImage("CF_B", null, null, "图中是一只小狗")));
    }

    /** 评分标准夹具：与随包配置同形（6 宏观 + 5 微观 + 叙事产生性 + 一组图片规则）。 */
    private RubricConfig rubric() {
        List<RubricConfig.Item> macro = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            macro.add(leveledItem(code.name(), code.displayName()));
        }
        macro.add(leveledItem(GROUP_RULE, "图1"));
        List<RubricConfig.Item> micro = new ArrayList<>();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            micro.add(leveledItem(code.name(), code.displayName()));
        }
        micro.add(new RubricConfig.Item("叙事产生性（量化的统计）", "平均句子长度（C单元）、形容词/副词/连词频次",
                ProductivityStat.ITEM_CODE, List.of(),
                "由AI结合儿童叙事表现和量化统计自主判断0、1或2分，并说明理由；不预设各等级的固定阈值。"));
        return new RubricConfig(2, RUBRIC_VERSION, macro, micro, null);
    }

    private RubricConfig.Item leveledItem(String code, String name) {
        return new RubricConfig.Item(name, "判定依据-" + code, code,
                List.of(new RubricConfig.Level(2, "2 分说明-" + code),
                        new RubricConfig.Level(1, "1 分说明-" + code),
                        new RubricConfig.Level(0, "0 分说明-" + code)), null);
    }

    private String validOutput(String snippet) {
        return output(macroDimensions(snippet), microDimensions(snippet),
                contentItems(snippet, GROUP_1 + ", " + GROUP_2));
    }

    private String macroDimensions(String snippet) {
        StringBuilder dims = new StringBuilder();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            dims.append(scoreEntry(code.name(), snippet)).append(',');
        }
        return dims.toString();
    }

    private String microDimensions(String snippet) {
        StringBuilder dims = new StringBuilder();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            dims.append(scoreEntry(code.name(), snippet)).append(',');
        }
        return dims.toString();
    }

    /** 只给第一个微观维度换一段（用来验证编造的引文会被丢弃）。 */
    private String microDimensionsExcluding(String snippetForFirst) {
        StringBuilder dims = new StringBuilder();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            dims.append(scoreEntry(code.name(),
                    code == MicroDimensionCode.VOCABULARY_DIVERSITY ? snippetForFirst : TEXT)).append(',');
        }
        return dims.toString();
    }

    private String contentItems(String snippet, String groupIdList) {
        StringBuilder items = new StringBuilder();
        for (String groupId : groupIdList.split(",")) {
            items.append(scoreEntry(groupId.trim(), snippet)).append(',');
        }
        return items.toString();
    }

    /** 一项判断：{@code "条目号": {score, reason, evidence}}。结构由 Schema 约束，服务端只认键。 */
    private String scoreEntry(String code, String snippet) {
        return """
                "%s": {"score": 2, "reason": "理由", "evidence": ["%s"]}""".formatted(code, snippet);
    }

    private String output(String macroDimensions, String microDimensions, String contentItems) {
        return """
                {"summary": "整体概述",
                 "macrostructure": {"dimensions": {%s}, "content_items": {%s}},
                 "microstructure": {"dimensions": {%s},
                   "productivity": {"score": 2, "reason": "理由", "evidence": []}}}"""
                .formatted(trimTrailingComma(macroDimensions), trimTrailingComma(contentItems),
                        trimTrailingComma(microDimensions));
    }

    private static String trimTrailingComma(String json) {
        return json.strip().endsWith(",") ? json.strip().substring(0, json.strip().length() - 1) : json;
    }

    private static class StubChat implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();
        String content = "{}";

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            return new ChatResponse(content, "ecnu-plus");
        }

        @Override
        public String modelFor(ChatRequest request) {
            return request.hasImages() ? "ecnu-plus" : "ecnu-max";
        }
    }
}
