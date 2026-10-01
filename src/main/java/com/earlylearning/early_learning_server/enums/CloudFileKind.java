package com.earlylearning.early_learning_server.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 云端文件类型。
 *
 * <p>契约只允许官方资料：{@code EXPORT}/{@code BACKUP} 是本地文件，不支持上传。
 * 枚举名即落库值与契约字面量，一字不差。
 */
public enum CloudFileKind {

    AUDIO("AUDIO"),
    PDF("PDF"),
    IMAGE("IMAGE");

    @EnumValue
    private final String value;

    CloudFileKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
