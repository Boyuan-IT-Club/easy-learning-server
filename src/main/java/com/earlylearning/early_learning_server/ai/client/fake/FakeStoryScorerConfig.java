package com.earlylearning.early_learning_server.ai.client.fake;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.earlylearning.early_learning_server.ai.model.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.scoring.Evidence;
import com.earlylearning.early_learning_server.ai.model.scoring.ModelMeta;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringGroup;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScore;
import com.earlylearning.early_learning_server.ai.model.scoring.story.AiScoreSection;
import com.earlylearning.early_learning_server.ai.model.scoring.story.MicrostructureSection;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreContentItem;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreDimension;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreValidator;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringInput;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;

/**
 * 故事评分的假实现：接入真实模型前用它把链路跑通。
 *
 * <p>行为可控（通过系统属性触发），供联调与测试使用：
 * <ul>
 *   <li>{@code ai.fake-scorer.fail=true} → 抛出可重试的调用失败</li>
 *   <li>{@code ai.fake-scorer.invalid-output=true} → 返回结构不合法的分数，
 *       用来验证"不合格输出不会变成成功结果"这条路径</li>
 * </ul>
 *
 * <p>与真实实现按 {@code ai.llm.provider} 互斥：{@code fake} 或缺省时用这个，
 * 配了厂商（如 {@code ecnu}）时让位给真实实现。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "fake", matchIfMissing = true)
public class FakeStoryScorerConfig {

    private static final Logger log = LoggerFactory.getLogger(FakeStoryScorerConfig.class);

    private static final String EVIDENCE_SOURCE = Evidence.SOURCE_TRANSCRIPT;
    private static final int SAMPLE_SCORE = 1;
    private static final int MAX_SCORE = 2;
    private static final int EVIDENCE_SAMPLE_LENGTH = 2;

    @Bean
    public StoryScorer fakeStoryScorer() {
        return (input, rubricVersion) -> {
            if (Boolean.getBoolean("ai.fake-scorer.fail")) {
                throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "示例：模型调用超时", true, null);
            }
            AiScore score = buildValidScore(input, rubricVersion);
            if (Boolean.getBoolean("ai.fake-scorer.invalid-output")) {
                // 故意漏掉一个维度：ScoreValidator 必须拒收，并落成 MODEL_OUTPUT_INVALID
                log.info("假评分器按要求产出非法输出");
                return dropOneDimension(score);
            }
            // 把"真的收到了图"打出来：图片解析发生在提交路径上，这里能看到解析结果
            log.info("假故事评分器被调用 分组={} 图片={}张 其中带字节={}张 rubricVersion={}",
                    input.contentItems().size(), input.images().size(),
                    input.images().stream().filter(ScoringImage::hasBytes).count(), rubricVersion);
            return score;
        };
    }

    private AiScore buildValidScore(StoryScoringInput input, String rubricVersion) {
        List<Evidence> evidence = sampleEvidence(input.confirmedText());

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
        for (ScoringGroup item : input.contentItems()) {
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
