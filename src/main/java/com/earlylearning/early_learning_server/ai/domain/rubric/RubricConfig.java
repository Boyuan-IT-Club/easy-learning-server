package com.earlylearning.early_learning_server.ai.domain.rubric;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 服务端评分标准（{@code /ai/rubric-config.json}）。
 *
 * <p>契约：「统一 0/1/2 分规则只保存在服务端评分配置」「服务端按 rubric_version 加载配置并注入评分提示词；
 * 版本发布后内容不可原地改变」。所以标准是随代码发布的资源，不是数据库内容、也不由请求携带。
 *
 * @param schemaVersion 评分结构版本
 * @param rubricVersion 标准版本；必须与 {@code ai.rubric.version} 一致（不一致会在启动时被 {@code infrastructure.rubric.RubricConfigLoader} 挡住）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RubricConfig(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("rubric_version") String rubricVersion,
        List<Item> macrostructure,
        List<Item> microstructure,
        @JsonProperty("question_reasoning") Item questionReasoning) {

    /**
     * 一个评分条目。
     *
     * @param item               显示名称
     * @param criterion          判定依据（写给模型看）
     * @param itemCode           条目编号，与 {@code ai.rubric} 的维度枚举、结果里的 {@code item_code} 对应
     * @param scoreLevels        0/1/2 三档说明；量化类条目（如叙事产生性）没有档位，为空
     * @param scoringInstruction 量化类条目的评分指示（配置原文），有它时提示词用它代替档位说明
     */
    public record Item(String item,
                       String criterion,
                       @JsonProperty("item_code") String itemCode,
                       @JsonProperty("score_levels") List<Level> scoreLevels,
                       @JsonProperty("scoring_instruction") String scoringInstruction) {
    }

    /** 某一档的分数与说明。 */
    public record Level(int score, String description) {
    }
}
