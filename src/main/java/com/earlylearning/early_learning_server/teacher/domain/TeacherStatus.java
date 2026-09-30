package com.earlylearning.early_learning_server.teacher.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 教师云端账号状态。落库为 V1 的 INT（1 启用 / 0 停用），与契约 TeacherBearer "status 为 1" 一致；
 * 对外（后台列表）显示为 ACTIVE / DISABLED。
 */
public enum TeacherStatus {

    ENABLED(1),
    DISABLED(0);

    @EnumValue
    private final int value;

    TeacherStatus(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 后台展示用的名字。 */
    public String display() {
        return this == ENABLED ? "ACTIVE" : "DISABLED";
    }
}
