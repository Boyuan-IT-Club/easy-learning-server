package com.earlylearning.early_learning_server.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 内容版本状态。状态在所属端生效：ACTIVE 可被新下载选择；DISABLED 禁止新选，
 * 但历史评估与课堂仍可按编号加版本读取原配置。
 */
public enum ContentStatus {

    ACTIVE("ACTIVE"),
    DISABLED("DISABLED");

    @EnumValue
    private final String value;

    ContentStatus(String value) {
        this.value = value;
    }

    /** 落库值。 */
    public String value() {
        return value;
    }
}
