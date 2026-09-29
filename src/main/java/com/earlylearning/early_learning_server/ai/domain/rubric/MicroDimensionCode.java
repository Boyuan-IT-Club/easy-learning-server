package com.earlylearning.early_learning_server.ai.domain.rubric;

/** 五个固定微观维度。取值是的，全部各出现一次。 */
public enum MicroDimensionCode {

    VOCABULARY_DIVERSITY("词汇丰富度"),
    MENTAL_STATE_WORDS("心理状态词"),
    SYNTACTIC_COMPLEXITY("句法复杂度"),
    REFERENTIAL_COHESION("指称衔接"),
    CONJUNCTION_COHESION("连词衔接");

    private final String displayName;

    MicroDimensionCode(String displayName) {
        this.displayName = displayName;
    }

    /** 评分规则中的显示名称。 */
    public String displayName() {
        return displayName;
    }
}
