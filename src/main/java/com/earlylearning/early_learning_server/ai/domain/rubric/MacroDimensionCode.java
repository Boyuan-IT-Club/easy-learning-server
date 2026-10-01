package com.earlylearning.early_learning_server.ai.domain.rubric;

/** 五个固定宏观维度，全部各出现一次；「细节拓展」不在宏观，它是图片分组条目 NARRATIVE_CONTENT_06（图7-2）。 */
public enum MacroDimensionCode {

    EVENT_SEQUENCE("事件顺序"),
    PLOT_STRUCTURE("情节结构"),
    THEME("主题体现"),
    COHERENCE("故事连贯性"),
    CAUSAL_LOGIC("因果逻辑");

    private final String displayName;

    MacroDimensionCode(String displayName) {
        this.displayName = displayName;
    }

    /** 评分规则中的显示名称。 */
    public String displayName() {
        return displayName;
    }
}
