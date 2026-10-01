package com.earlylearning.early_learning_server.ai.model.scoring.story;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreValidator;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreDimension;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreContentItem;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;
import com.earlylearning.early_learning_server.ai.model.scoring.story.MicrostructureSection;
import com.earlylearning.early_learning_server.ai.model.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.model.scoring.EvidenceValidator;
import com.earlylearning.early_learning_server.ai.model.scoring.Evidence;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScoreSection;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScore;

import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.model.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreValidatorTests {

    private static final String RUBRIC_VERSION = "RUBRIC_2026_01";
    private static final String TEXT = "小明先说他想去公园，然后又说要带小狗。";

    private final ScoreValidator validator = new ScoreValidator(new EvidenceValidator());
    private final List<ScoringGroup> requestedItems = List.of(
            new ScoringGroup("GROUP_1", List.of("CF_A"), "IMG_ITEM_1"),
            new ScoringGroup("GROUP_2", List.of("CF_B"), "IMG_ITEM_2"));

    @Test
    void acceptsAWellFormedScore() {
        assertThatCode(() -> validator.validate(validScore(), RUBRIC_VERSION, TEXT, requestedItems))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongSchemaVersion() {
        AiScore score = withMacro(validScore(), macroDimensions(), validMacroContentItems(), 3);
        assertThatThrownBy(() -> validator.validate(score, RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("结构版本");
    }

    @Test
    void rejectsRubricVersionDifferentFromTheTask() {
        assertThatThrownBy(() -> validator.validate(validScore(), "RUBRIC_OTHER", TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("评分标准版本");
    }

    @Test
    void rejectsMissingOrDuplicatedDimension() {
        // 真正的重复：EVENT_SEQUENCE 出现两次、COHERENCY 缺失
        List<ScoreDimension> duplicated = new ArrayList<>();
        duplicated.add(dimension("EVENT_SEQUENCE"));
        duplicated.add(dimension("EVENT_SEQUENCE"));
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            if (code != MacroDimensionCode.EVENT_SEQUENCE && code != MacroDimensionCode.COHERENCE) {
                duplicated.add(dimension(code.name()));
            }
        }
        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), duplicated, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("重复或缺失");

        // 数量不足
        List<ScoreDimension> shortList = macroDimensions().subList(0, 4);
        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), shortList, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("维度数量");
    }

    @Test
    void rejectsDimensionsOutOfRuleOrder() {
        List<ScoreDimension> shuffled = new ArrayList<>(macroDimensions());
        java.util.Collections.swap(shuffled, 0, 1);

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), shuffled, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("顺序或集合");
    }

    @Test
    void rejectsScoreOutsideZeroToTwo() {
        ScoreDimension bad = new ScoreDimension(3, 2, "越界", List.of(), "EVENT_SEQUENCE", "事件顺序");
        List<ScoreDimension> dimensions = new ArrayList<>(macroDimensions());
        dimensions.set(0, bad);

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), dimensions, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("分数越界");
    }

    @Test
    void rejectsEvidenceThatDoesNotMatchTheCitedText() {
        // 偏移指向"小明先说他想去公园"，却声称证据是另一段话
        ScoreDimension fabricated = new ScoreDimension(2, 2, "编造引文",
                List.of(Evidence.transcript("这句话原文里没有", 0, 9)), "EVENT_SEQUENCE", "事件顺序");
        List<ScoreDimension> dimensions = new ArrayList<>(macroDimensions());
        dimensions.set(0, fabricated);

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), dimensions, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("与原文不一致");
    }

    @Test
    void rejectsEvidenceOffsetsOutsideTheText() {
        ScoreDimension outOfRange = new ScoreDimension(2, 2, "越界偏移",
                List.of(Evidence.transcript("x", 0, TEXT.length() + 5)), "EVENT_SEQUENCE", "事件顺序");
        List<ScoreDimension> dimensions = new ArrayList<>(macroDimensions());
        dimensions.set(0, outOfRange);

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), dimensions, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("偏移越界");
    }

    @Test
    void rejectsContentItemsThatDoNotCoverTheRequest() {
        List<ScoreContentItem> onlyOne = List.of(contentItem("GROUP_1", "IMG_ITEM_1"));

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), macroDimensions(), onlyOne), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("与请求的分组不一致");
    }

    @Test
    void acceptsEmptyEvidenceBecauseNoFabricationIsBetterThanFabrication() {
        List<ScoreDimension> noEvidence = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            noEvidence.add(new ScoreDimension(1, 2, "无证据", List.of(), code.name(), code.displayName()));
        }

        assertThatCode(() -> validator.validate(
                withMacro(validScore(), noEvidence, validMacroContentItems()), RUBRIC_VERSION, TEXT, requestedItems))
                .doesNotThrowAnyException();
    }

    private AiScore validScore() {
        return new AiScore(2, RUBRIC_VERSION, "概述",
                new AiScoreSection(macroDimensions(), validMacroContentItems()),
                new MicrostructureSection(microDimensions(), validProductivity()),
                new ModelMeta("fake-model", "PROMPT_V1"));
    }

    private AiScore withMacro(AiScore base, List<ScoreDimension> dimensions, List<ScoreContentItem> contentItems) {
        return new AiScore(base.schemaVersion(), base.rubricVersion(), base.summary(),
                new AiScoreSection(dimensions, contentItems), base.microstructure(), base.modelMeta());
    }

    private AiScore withMacro(AiScore base, List<ScoreDimension> dimensions, List<ScoreContentItem> contentItems,
                              int schemaVersion) {
        return new AiScore(schemaVersion, base.rubricVersion(), base.summary(),
                new AiScoreSection(dimensions, contentItems), base.microstructure(), base.modelMeta());
    }

    @Test
    void rejectsMissingProductivityBecauseMicrostructureIsNotShapedLikeMacrostructure() {
        // 微观结构是 {dimensions, productivity}：它没有 content_items，但 productivity 必填。
        // 早先两者共用同一形状，导致微观被要求返回不该有的 content_items，而 productivity 被漏掉。
        AiScore withoutProductivity = new AiScore(2, RUBRIC_VERSION, "概述",
                new AiScoreSection(macroDimensions(), validMacroContentItems()),
                new MicrostructureSection(microDimensions(), null),
                new ModelMeta("fake-model", "PROMPT_V1"));

        assertThatThrownBy(() -> validator.validate(withoutProductivity, RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("叙事产生性");
    }

    @Test
    void rejectsContentItemMappedToTheWrongRubricItem() {
        // 契约：图片条目的 rubric_item_code 与分组规则映射要完全一致
        List<ScoreContentItem> mismatched = List.of(
                contentItem("GROUP_1", "IMG_ITEM_2"), contentItem("GROUP_2", "IMG_ITEM_2"));

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), macroDimensions(), mismatched), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("评分条目与请求不一致");
    }

    @Test
    void rejectsFabricatedEvidenceInsideAContentItem() {
        // 图片条目里的引文同样是引文：编造的必须被挡下，不能只看维度
        List<ScoreContentItem> fabricated = new java.util.ArrayList<>(validMacroContentItems());
        fabricated.set(0, new ScoreContentItem(2, 2, "理由",
                List.of(Evidence.transcript("原文里没有这句话", 0, 9)), "GROUP_1", "IMG_ITEM_1"));

        assertThatThrownBy(() -> validator.validate(
                withMacro(validScore(), macroDimensions(), fabricated), RUBRIC_VERSION, TEXT, requestedItems))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("与原文不一致");
    }

    private ProductivityStat validProductivity() {
        return new ProductivityStat(null, null, null, null, 1, 2, "理由",
                List.of(Evidence.transcript("小明", 0, 2)), ProductivityStat.ITEM_CODE);
    }

    private List<ScoreDimension> macroDimensions() {
        List<ScoreDimension> dimensions = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            dimensions.add(dimension(code.name()));
        }
        return dimensions;
    }

    private List<ScoreDimension> microDimensions() {
        List<ScoreDimension> dimensions = new ArrayList<>();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            dimensions.add(new ScoreDimension(1, 2, "理由", List.of(), code.name(), code.displayName()));
        }
        return dimensions;
    }

    private ScoreDimension dimension(String code) {
        return new ScoreDimension(1, 2, "理由", List.of(Evidence.transcript("小明", 0, 2)), code, code);
    }

    private List<ScoreContentItem> validMacroContentItems() {
        return List.of(contentItem("GROUP_1", "IMG_ITEM_1"), contentItem("GROUP_2", "IMG_ITEM_2"));
    }

    private ScoreContentItem contentItem(String id, String rubricItemCode) {
        return new ScoreContentItem(2, 2, "理由", List.of(), id, rubricItemCode);
    }
}
