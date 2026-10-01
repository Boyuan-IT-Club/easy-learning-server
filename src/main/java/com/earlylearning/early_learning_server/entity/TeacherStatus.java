package com.earlylearning.early_learning_server.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 教师云端账号状态。落库与契约 {@code UserStatus} 都是整数：1 启用，0 禁用；与本地离线登录无关。
 */
public enum TeacherStatus {

    DISABLED(0),
    ENABLED(1);

    @EnumValue
    private final int value;

    TeacherStatus(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** @throws BusinessException 400，field_path 为给定位置 */
    public static TeacherStatus of(Integer value, String fieldPath) {
        if (value != null) {
            for (TeacherStatus status : values()) {
                if (status.value == value) {
                    return status;
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
    }
}
