package com.earlylearning.early_learning_server.ai.model.scoring.story;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.Evidence;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微观结构里的「叙事产生性」条目 —— 它是独立字段，不是 {@code dimensions} 里的一项。
 *
 * <p>契约的 ai_score 形状：{@code macrostructure{dimensions, content_items}} 与
 * {@code microstructure{dimensions, productivity}}。叙事产生性有四项量化统计，
 * 但「四项量化统计不单独作为轴，其 AI 0/1/2 分才是该维度的值」——
 * 所以它是一个带分数与理由的对象，不是一个数字。
 *
 * @param meanCUnitLength  平均小句长度；统计不出来时为 null
 * @param adjectiveCount   形容词数；同上
 * @param adverbCount      副词数；同上
 * @param conjunctionCount 连接词数；同上
 * @param score            本条的 AI 评分，0/1/2
 * @param evidence         可为空数组，但不能编造引文
 * @param itemCode         固定为 {@value #ITEM_CODE}
 */
public record ProductivityStat(

        @JsonProperty("mean_c_unit_length") Double meanCUnitLength,
        @JsonProperty("adjective_count") Integer adjectiveCount,
        @JsonProperty("adverb_count") Integer adverbCount,
        @JsonProperty("conjunction_count") Integer conjunctionCount,
        @JsonProperty("score") Integer score,
        @JsonProperty("max_score") Integer maxScore,
        @JsonProperty("reason") String reason,
        @JsonProperty("evidence") List<Evidence> evidence,
        @JsonProperty("item_code") String itemCode) {

    public static final String ITEM_CODE = "NARRATIVE_PRODUCTIVITY";
}
