package com.earlylearning.early_learning_server.ai.adapter.fake;

import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.port.StoryScorer;
import com.earlylearning.early_learning_server.ai.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.score.AiScore;
import com.earlylearning.early_learning_server.ai.score.AiScoreSection;
import com.earlylearning.early_learning_server.ai.score.Evidence;
import com.earlylearning.early_learning_server.ai.score.MicrostructureSection;
import com.earlylearning.early_learning_server.ai.score.ModelMeta;
import com.earlylearning.early_learning_server.ai.score.ProductivityStat;
import com.earlylearning.early_learning_server.ai.score.ScoreContentItem;
import com.earlylearning.early_learning_server.ai.score.ScoreDimension;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.web.ContentItem;
import com.earlylearning.early_learning_server.ai.web.StoryScoringRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 故事评分的假实现：接入真实模型前用它把链路跑通。
 *
 * <p>行为可控（通过系统属性触发），供联调与测试使用：
 * <ul>
 *   <li>{@code ai.fake-scorer.fail=true} → 抛出可重试的调用失败</li>
 *   <li>{@code ai.fake-scorer.invalid-output=true} → **返回结构不合法的分数**，
 *       用来验证"不合格输出不会变成成功结果"这条路径</li>
 * </ul>
 *
 * <p>接入真实实现时，提供自己的 {@link StoryScorer} Bean 并标注 {@code @Primary} 即可覆盖。
 */
@Configuration(proxyBeanMethods = false)
public class FakeStoryScorerConfig {

    private static final Logger log = LoggerFactory.getLogger(FakeStoryScorerConfig.class);

    private static final String EVIDENCE_SOURCE = Evidence.SOURCE_TRANSCRIPT;
    private static final int SAMPLE_SCORE = 1;
    private static final int MAX_SCORE = 2;
    private static final int EVIDENCE_SAMPLE_LENGTH = 2;

    @Bean
    public StoryScorer fakeStoryScorer() {
        return (request, rubricVersion) -> {
            if (Boolean.getBoolean("ai.fake-scorer.fail")) {
                throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "示例：模型调用超时", true, null);
            }
            AiScore score = buildValidScore(request, rubricVersion);
            if (Boolean.getBoolean("ai.fake-scorer.invalid-output")) {
                // 故意漏掉一个维度：ScoreValidator 必须拒收，并落成 MODEL_OUTPUT_INVALID
                log.info("假评分器按要求产出非法输出");
                return dropOneDimension(score);
            }
            log.info("假评分器被调用 groups={} rubricVersion={}",
                    request.contentItems().size(), rubricVersion);
            return score;
        };
    }

    private AiScore buildValidScore(StoryScoringRequest request, String rubricVersion) {
        List<Evidence> evidence = sampleEvidence(request.confirmedText());

        List<ScoreDimension> macro = new ArrayList<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            macro.add(new ScoreDimension(SAMPLE_SCORE, MAX_SCORE, "示例理由", evidence, code.name(), code.displayName()));
        }
        List<ScoreDimension> micro = new ArrayList<>();
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            micro.add(new ScoreDimension(SAMPLE_SCORE, MAX_SCORE, "示例理由", evidence, code.name(), code.displayName()));
        }

        ProductivityStat productivity = new ProductivityStat(
                null, null, null, null, SAMPLE_SCORE, MAX_SCORE,
                "示例理由：假实现固定输出", evidence, ProductivityStat.ITEM_CODE);

        List<ScoreContentItem> contentItems = new ArrayList<>();
        for (ContentItem item : request.contentItems()) {
            contentItems.add(new ScoreContentItem(SAMPLE_SCORE, MAX_SCORE, "示例理由", evidence,
                    item.contentItemId(), item.rubricItemCode()));
        }

        return new AiScore(AiScore.SCHEMA_VERSION, rubricVersion, "示例概述",
                new AiScoreSection(macro, contentItems),
                new MicrostructureSection(micro, productivity),
                new ModelMeta("fake-story-model", "PROMPT_V1"));
    }

    /** 确认文本为空时不给任何证据——宁可没有证据，也不编造引文。 */
    private List<Evidence> sampleEvidence(String confirmedText) {
        if (confirmedText == null || confirmedText.isEmpty()) {
            return List.of();
        }
        int end = Math.min(EVIDENCE_SAMPLE_LENGTH, confirmedText.length());
        return List.of(new Evidence(EVIDENCE_SOURCE, confirmedText.substring(0, end), 0, end));
    }

    private AiScore dropOneDimension(AiScore score) {
        List<ScoreDimension> short6 = new ArrayList<>(score.macrostructure().dimensions());
        short6.remove(0);
        return new AiScore(score.schemaVersion(), score.rubricVersion(), score.summary(),
                new AiScoreSection(short6, score.macrostructure().contentItems()),
                score.microstructure(), score.modelMeta());
    }
}
