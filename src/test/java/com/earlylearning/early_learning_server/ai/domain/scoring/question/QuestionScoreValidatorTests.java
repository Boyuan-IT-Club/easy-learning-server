package com.earlylearning.early_learning_server.ai.domain.scoring.question;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.QuestionScoreValidator;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.QuestionAiScore;
import com.earlylearning.early_learning_server.ai.domain.scoring.ModelMeta;
import com.earlylearning.early_learning_server.ai.domain.scoring.InvalidModelOutputException;
import com.earlylearning.early_learning_server.ai.domain.scoring.EvidenceValidator;
import com.earlylearning.early_learning_server.ai.domain.scoring.Evidence;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScoringOutput;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单题评分的运行时校验：契约里那些「Schema 表达不了、必须代码查」的规则。
 *
 * <p>重点是「null 不能作为成功结果」——enum [0,1,2] 无法表达"缺字段"，
 * 少了这条检查，一个没给出分数的模型输出会变成一份成功的 0 分。
 */
class QuestionScoreValidatorTests {

    private static final String VERSION = "RUBRIC_2026_01";
    private static final ModelMeta META = new ModelMeta("m", "PROMPT_V1");
    private static final String TEXT = "小明说他想去公园，后来又说要带小狗。";

    private final QuestionScoreValidator validator =
            new QuestionScoreValidator(new EvidenceValidator());

    @Test
    void acceptsAValidScore() {
        assertThatCode(() -> validator.validate(valid(), VERSION, TEXT)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingScoreInsteadOfTreatingItAsZero() {
        assertThatThrownBy(() -> validator.validate(score(null, 2, "理由", evidence()), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("null");
    }

    @Test
    void rejectsOutOfRangeScore() {
        assertThatThrownBy(() -> validator.validate(score(3, 2, "理由", evidence()), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("越界");
        assertThatThrownBy(() -> validator.validate(score(-1, 2, "理由", evidence()), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class);
    }

    @Test
    void rejectsWrongMaxScore() {
        assertThatThrownBy(() -> validator.validate(score(1, 3, "理由", evidence()), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("满分");
    }

    @Test
    void rejectsMissingReason() {
        assertThatThrownBy(() -> validator.validate(score(1, 2, "  ", evidence()), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("理由");
    }

    @Test
    void rejectsVersionDifferentFromTheTask() {
        assertThatThrownBy(() -> validator.validate(valid(), "RUBRIC_OLD", TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("版本");
    }

    @Test
    void rejectsFabricatedEvidence() {
        // 引文在原文里不存在——这正是"不能编造引文"要挡的
        List<Evidence> fabricated = List.of(new Evidence(Evidence.SOURCE_TRANSCRIPT, "孩子说他要去看奶奶", 0, 9));

        assertThatThrownBy(() -> validator.validate(score(1, 2, "理由", fabricated), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejectsEvidenceWithOutOfRangeOffsets() {
        List<Evidence> beyond = List.of(new Evidence(Evidence.SOURCE_TRANSCRIPT, "小明", 0, 999));

        assertThatThrownBy(() -> validator.validate(score(1, 2, "理由", beyond), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("越界");
    }

    @Test
    void acceptsEmptyEvidence() {
        //  evidence 可以为空数组；空不等于编造
        assertThatCode(() -> validator.validate(score(0, 2, "答非所问", List.of()), VERSION, TEXT))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingEvidenceField() {
        // 契约把 evidence 列为必填：可以给空数组，但不能整个字段缺失
        assertThatThrownBy(() -> validator.validate(score(1, 2, "理由", null), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("证据字段");
    }

    @Test
    void rejectsMissingModelMeta() {
        QuestionAiScore score = valid().score();

        assertThatThrownBy(() -> validator.validate(new AnswerScoringOutput(score, null), VERSION, TEXT))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("模型信息");
    }

    private AnswerScoringOutput valid() {
        return score(1, 2, "示例理由", evidence());
    }

    /** 校验器吃的是适配器的完整输出（分数 + 模型元数据），所以用例也按这个形状构造。 */
    private AnswerScoringOutput score(Integer value, Integer maxScore, String reason, List<Evidence> evidence) {
        return new AnswerScoringOutput(new QuestionAiScore(VERSION, value, maxScore, reason, evidence), META);
    }

    private List<Evidence> evidence() {
        List<Evidence> list = new ArrayList<>();
        list.add(new Evidence(Evidence.SOURCE_TRANSCRIPT, TEXT.substring(0, 2), 0, 2));
        return list;
    }
}
