package com.earlylearning.early_learning_server.license.domain;

/**
 * 激活码状态。
 *
 * <pre>
 *   UNUSED ──注册占用──► ACTIVE
 *     │                   │
 *     └──撤销──► REVOKED ◄──撤销──┘
 * </pre>
 *
 * <p>全部不可逆，REVOKED 是终态。撤销 ACTIVE 码等于永久终止该教师的云端访问；
 * 临时停用应该停用教师账号，而不是撤销激活码。枚举名即落库值，与迁移里的 CHECK 约束一致。
 */
public enum LicenseStatus {

    UNUSED,
    ACTIVE,
    REVOKED;

    public boolean canTransitionTo(LicenseStatus target) {
        return switch (this) {
            case UNUSED -> target == ACTIVE || target == REVOKED;
            case ACTIVE -> target == REVOKED;
            case REVOKED -> false;
        };
    }
}
