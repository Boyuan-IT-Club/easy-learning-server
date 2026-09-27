package com.earlylearning.early_learning_server.ai.score;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.earlylearning.early_learning_server.ai.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.web.ContentItem;
import org.springframework.stereotype.Component;

/**
 * 评分结果的运行时语义校验。
 *
 * <p>契约明确：Schema 的 {@code uniqueItems} **只能拒绝完全相同的条目**，
 * 维度集合的完整/唯一、分数范围、证据与原文的一致性、图片分组的覆盖情况，
 * 都必须在代码里查——"不能仅凭 Schema 成功"。
 *
 * <p>校验不通过时抛 {@link InvalidModelOutputException}，由任务执行层落成
 * {@code MODEL_OUTPUT_INVALID} 失败；**绝不返回一个"看起来成功"的分数**。
 */
@Component
public class ScoreValidator {

    private static final int MAX_SCORE = 2;
    private static final int MIN_SCORE = 0;

    private final EvidenceValidator evidenceValidator;

    public ScoreValidator(EvidenceValidator evidenceValidator) {
        this.evidenceValidator = evidenceValidator;
    }

    public void validate(AiScore score,
                         String expectedRubricVersion,
                         String confirmedText,
                         List<ContentItem> requestedItems) {
        require(score != null, "缺少评分结果");
        require(Objects.equals(score.schemaVersion(), AiScore.SCHEMA_VERSION),
                "评分结构版本不是 " + AiScore.SCHEMA_VERSION);
        require(Objects.equals(score.rubricVersion(), expectedRubricVersion),
                "评分标准版本与任务记录不一致：" + score.rubricVersion() + " ≠ " + expectedRubricVersion);
        require(score.modelMeta() != null, "缺少模型信息");

        validateMacrostructure(score.macrostructure(), requestedItems, confirmedText);
        validateMicrostructure(score.microstructure(), confirmedText);
    }

    /** 宏观：6 个固定维度 + **恰好覆盖**请求全部分组的图片条目。 */
    private void validateMacrostructure(AiScoreSection macro, List<ContentItem> requestedItems, String confirmedText) {
        require(macro != null, "缺少宏观结构");
        validateDimensions(macro.dimensions(), "宏观", macroCodes(), confirmedText);
        requireSameContentItems(macro, requestedItems, "宏观", confirmedText);
    }

    /** 微观：5 个固定维度 + **叙事产生性**（独立字段，且必填）。 */
    private void validateMicrostructure(MicrostructureSection micro, String confirmedText) {
        require(micro != null, "缺少微观结构");
        validateDimensions(micro.dimensions(), "微观", microCodes(), confirmedText);

        ProductivityStat productivity = micro.productivity();
        require(productivity != null, "缺少叙事产生性（microstructure.productivity）");
        require(ProductivityStat.ITEM_CODE.equals(productivity.itemCode()),
                "叙事产生性的 item_code 应为 " + ProductivityStat.ITEM_CODE
                        + "，实际 " + productivity.itemCode());
        require(productivity.score() != null, "叙事产生性缺少分数");
        require(productivity.score() >= MIN_SCORE && productivity.score() <= MAX_SCORE,
                "叙事产生性分数越界：" + productivity.score());
        require(Objects.equals(productivity.maxScore(), MAX_SCORE),
                "叙事产生性满分应为 " + MAX_SCORE);
        require(hasText(productivity.reason()), "叙事产生性缺少评分理由");
        evidenceValidator.validate(productivity.evidence(), "叙事产生性", confirmedText);
    }

    /** 维度集合必须**按规则顺序、完整且不重复**出现。 */
    private void validateDimensions(List<ScoreDimension> dimensions,
                                    String label,
                                    List<String> expectedCodes,
                                    String confirmedText) {
        require(dimensions != null, label + "维度缺失");
        require(dimensions.size() == expectedCodes.size(),
                label + "维度数量应为 " + expectedCodes.size() + "，实际 " + dimensions.size());

        Set<String> seen = new HashSet<>();
        List<String> actualCodes = new ArrayList<>();
        for (ScoreDimension dimension : dimensions) {
            require(dimension != null, label + "维度有空条目");
            require(seen.add(dimension.itemCode()), label + "维度重复或缺失：" + dimension.itemCode());
            actualCodes.add(dimension.itemCode());
            validateDimension(dimension, label, confirmedText);
        }
        require(actualCodes.equals(expectedCodes), label + "维度顺序或集合与规则不一致：" + actualCodes);
    }

    private void validateDimension(ScoreDimension dimension, String label, String confirmedText) {
        require(dimension.score() != null, label + "维度缺少分数：" + dimension.itemCode());
        require(dimension.score() >= MIN_SCORE && dimension.score() <= MAX_SCORE,
                label + "维度分数越界：" + dimension.itemCode() + "=" + dimension.score());
        require(Objects.equals(dimension.maxScore(), MAX_SCORE),
                label + "维度满分应为 " + MAX_SCORE + "：" + dimension.itemCode());
        require(hasText(dimension.reason()), label + "维度缺少评分理由：" + dimension.itemCode());
        require(hasText(dimension.itemName()), label + "维度缺少显示名称：" + dimension.itemCode());
        evidenceValidator.validate(dimension.evidence(), label + "维度 " + dimension.itemCode(), confirmedText);
    }

    /**
     * 图片条目必须**恰好覆盖**请求里的全部分组：不能多、不能漏，
     * 且 {@code rubric_item_code} 要与请求中该分组的映射一致——契约要求「与分组规则映射完全一致」。
     * 证据同样要过校验：图片条目里的引文也是引文，「不能编造」对它一样成立。
     */
    private void requireSameContentItems(AiScoreSection section, List<ContentItem> requested,
                                        String label, String confirmedText) {
        java.util.Map<String, ContentItem> expectedByGroup = new java.util.HashMap<>();
        for (ContentItem item : requested) {
            expectedByGroup.put(item.contentItemId(), item);
        }
        Set<String> expected = expectedByGroup.keySet();
        Set<String> actual = new HashSet<>();
        if (section.contentItems() != null) {
            for (ScoreContentItem item : section.contentItems()) {
                require(item != null, label + "图片条目为空");
                require(actual.add(item.contentItemId()),
                        label + "图片条目重复：" + item.contentItemId());
                require(item.score() != null && item.score() >= MIN_SCORE && item.score() <= MAX_SCORE,
                        label + "图片条目分数越界：" + item.contentItemId());
                require(Objects.equals(item.maxScore(), MAX_SCORE),
                        label + "图片条目满分应为 " + MAX_SCORE + "：" + item.contentItemId());
                require(hasText(item.reason()), label + "图片条目缺少理由：" + item.contentItemId());
                ContentItem requestedItem = expectedByGroup.get(item.contentItemId());
                if (requestedItem != null) {
                    require(Objects.equals(item.rubricItemCode(), requestedItem.rubricItemCode()),
                            label + "图片条目的评分条目与请求不一致：" + item.contentItemId()
                                    + " 应为 " + requestedItem.rubricItemCode() + "，返回 " + item.rubricItemCode());
                }
                evidenceValidator.validate(item.evidence(),
                        label + "图片条目 " + item.contentItemId(), confirmedText);
            }
        }
        require(actual.equals(expected),
                label + "图片条目与请求的分组不一致：请求 " + expected + "，返回 " + actual);
    }

    private List<String> macroCodes() {
        List<String> codes = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            codes.add(code.name());
        }
        return codes;
    }

    private List<String> microCodes() {
        List<String> codes = new ArrayList<>();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            codes.add(code.name());
        }
        return codes;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidModelOutputException(message);
        }
    }
}
