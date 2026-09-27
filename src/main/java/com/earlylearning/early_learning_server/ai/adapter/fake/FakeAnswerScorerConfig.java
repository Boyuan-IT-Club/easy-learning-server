package com.earlylearning.early_learning_server.ai.adapter.fake;

import java.util.List;

import com.earlylearning.early_learning_server.ai.port.AnswerScorer;
import com.earlylearning.early_learning_server.ai.score.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.score.Evidence;
import com.earlylearning.early_learning_server.ai.score.ModelMeta;
import com.earlylearning.early_learning_server.ai.score.QuestionAiScore;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.web.AnswerScoringRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 单题评分的假实现：接入真实模型前用它把链路跑通。
 *
 * <p>行为可控（通过系统属性触发），供联调与测试使用：
 * <ul>
 *   <li>{@code ai.fake-answer-scorer.fail=true} → 抛出可重试的调用失败</li>
 *   <li>{@code ai.fake-answer-scorer.invalid-output=true} → **不给出分数**，
 *       用来验证契约那句「null 不能作为成功结果」真的被执行，而不是变成一份成功的 0 分</li>
 * </ul>
 *
 * <p>理由与证据都是固定内容：不给儿童数据留任何进入文本的路径。
 */
@Configuration(proxyBeanMethods = false)
public class FakeAnswerScorerConfig {

    private static final Logger log = LoggerFactory.getLogger(FakeAnswerScorerConfig.class);

    private static final int SAMPLE_SCORE = 1;
    private static final int EVIDENCE_SAMPLE_LENGTH = 2;
    private static final String SAMPLE_REASON = "示例理由：假实现固定输出";

    @Bean
    public AnswerScorer fakeAnswerScorer() {
        return (request, rubricVersion) -> {
            if (Boolean.getBoolean("ai.fake-answer-scorer.fail")) {
                throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "示例：模型调用超时", true, null);
            }
            if (Boolean.getBoolean("ai.fake-answer-scorer.invalid-output")) {
                log.info("假单题评分器按要求产出非法输出：缺分数");
                return new AnswerScoringOutput(
                        new QuestionAiScore(rubricVersion, null, QuestionAiScore.MAX_SCORE, SAMPLE_REASON, List.of()),
                        modelMeta());
            }
            log.info("假单题评分器被调用 attempt={} questionId={} rubricVersion={}",
                    request.attempt(), request.question().questionId(), rubricVersion);
            return new AnswerScoringOutput(buildValidScore(request, rubricVersion), modelMeta());
        };
    }

    private QuestionAiScore buildValidScore(AnswerScoringRequest request, String rubricVersion) {
        return new QuestionAiScore(rubricVersion, SAMPLE_SCORE, QuestionAiScore.MAX_SCORE, SAMPLE_REASON,
                sampleEvidence(request.confirmedText()));
    }

    /** 确认文本为空时不给任何证据——宁可没有证据，也不编造引文。 */
    private List<Evidence> sampleEvidence(String confirmedText) {
        if (confirmedText == null || confirmedText.isEmpty()) {
            return List.of();
        }
        int end = Math.min(EVIDENCE_SAMPLE_LENGTH, confirmedText.length());
        return List.of(new Evidence(Evidence.SOURCE_TRANSCRIPT, confirmedText.substring(0, end), 0, end));
    }

    private ModelMeta modelMeta() {
        return new ModelMeta("fake-answer-model", "PROMPT_V1");
    }
}
