package com.earlylearning.early_learning_server.identity.entity;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/** 管理员状态（契约 {@code AdminStatus}）。枚举名即落库值，与 V1 的 CHECK 约束一致。 */
public enum AdminStatus {
    ACTIVE,
    DISABLED;

    /** 解析请求体里的取值；非法值 400 并指向该字段。 */
    public static AdminStatus parse(String value, String fieldPath) {
        if (value != null) {
            for (AdminStatus status : values()) {
                if (status.name().equals(value)) {
                    return status;
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
    }
}
