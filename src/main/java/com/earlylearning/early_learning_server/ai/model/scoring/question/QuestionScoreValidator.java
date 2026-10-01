package com.earlylearning.early_learning_server.ai.model.scoring.question;
import com.earlylearning.early_learning_server.ai.model.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.model.scoring.EvidenceValidator;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * 单题评分的运行时语义校验。
 *
 * <p>契约对单题评分的要求里硬的一条是：「null 不能作为成功结果」。
 * Schema 的 {@code enum: [0,1,2]} 不能表达"缺字段"，所以必须在这里挡下来——
 * 否则一个没给出分数的模型输出会变成一份"成功的 0 分"。
 *
 * <p>校验不通过时抛 {@link InvalidModelOutputException}，由任务执行层落成
 * {@code MODEL_OUTPUT_INVALID} 失败。
 *
 * <p>与故事评分的校验器不同，这里没有"与请求比对"的检查：模型只给出分数与元数据，
 * 题号与 {@code attempt} 由服务端按请求写入结果，模型无从弄错。
 */
@Component
public class QuestionScoreValidator {

    private final EvidenceValidator evidenceValidator;

    public QuestionScoreValidator(EvidenceValidator evidenceValidator) {
        this.evidenceValidator = evidenceValidator;
    }

    /**
     * @param output                模型返回的分数与元数据
     * @param expectedRubricVersion 任务记录的实际版本
     * @param confirmedText         教师确认的原文，证据必须取自其中
     */
    public void validate(AnswerScoringOutput output, String expectedRubricVersion, String confirmedText) {
        require(output != null, "缺少评分结果");
        require(output.modelMeta() != null, "缺少模型信息");

        QuestionAiScore score = output.score();
        require(score != null, "缺少评分结果");
        require(Objects.equals(score.rubricVersion(), expectedRubricVersion),
                "评分标准版本与任务记录不一致：" + score.rubricVersion() + " ≠ " + expectedRubricVersion);

        require(score.score() != null, "缺少分数：成功评分不能是 null");
        require(score.score() >= 0 && score.score() <= QuestionAiScore.MAX_SCORE,
                "分数越界：" + score.score());
        require(Objects.equals(score.maxScore(), QuestionAiScore.MAX_SCORE),
                "满分应为 " + QuestionAiScore.MAX_SCORE + "，实际 " + score.maxScore());
        require(hasText(score.reason()), "缺少评分理由");

        require(score.evidence() != null, "缺少证据字段（可以是空数组，但不能缺失）");
        evidenceValidator.validate(score.evidence(), "单题评分", confirmedText);
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
