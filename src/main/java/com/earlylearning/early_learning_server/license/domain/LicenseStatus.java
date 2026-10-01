package com.earlylearning.early_learning_server.license.domain;

/**
 * 激活码状态（契约 {@code LicenseStatus}）：UNUSED → ACTIVE（注册占用）；UNUSED / ACTIVE → REVOKED。
 * REVOKED 不可恢复。枚举名即落库值，与 V1 的 CHECK 约束一致。
 */
public enum LicenseStatus {
    UNUSED,
    ACTIVE,
    REVOKED
}
