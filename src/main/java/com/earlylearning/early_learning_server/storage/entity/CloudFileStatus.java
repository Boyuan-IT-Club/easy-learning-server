package com.earlylearning.early_learning_server.storage.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 云端文件状态。
 *
 * <p>只有字节与记录都已提交且通过校验才可能为 {@link #READY}；只有 READY 才允许签发下载地址。
 * 枚举名即落库值与契约字面量，并与迁移里的 CHECK 约束保持一致。
 */
public enum CloudFileStatus {

    UPLOADING("UPLOADING"),
    READY("READY"),
    INVALID("INVALID"),
    DELETED("DELETED");

    @EnumValue
    private final String value;

    CloudFileStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
