package com.earlylearning.early_learning_server.license.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 一枚激活码（{@code user_license}）。
 *
 * <p>状态流转只通过 {@link #claimBy}、{@link #revoke} 进行，规则见 {@link LicenseStatus}。
 * setter 留给 MyBatis 映射使用，业务代码不要直接改 status。
 */
@TableName("user_license")
public class License {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private String activationCodeHash;
    private LicenseStatus status;
    private Instant activatedAt;
    private String codeHint;
    private String remark;
    private Integer createdBy;
    private Instant createdAt;
    private Instant revokedAt;
    private Integer revokedBy;
    private String revokeReason;
    private Instant updatedAt;

    /** 新生成的一枚码，状态 UNUSED。 */
    public static License issue(String codeHash, String codeHint, String remark, int adminId) {
        License license = new License();
        license.activationCodeHash = codeHash;
        license.codeHint = codeHint;
        license.remark = remark;
        license.createdBy = adminId;
        license.status = LicenseStatus.UNUSED;
        return license;
    }

    /** 注册占用：只有 UNUSED 可以占用。 */
    public void claimBy(int teacherId, Instant now) {
        if (!status.canTransitionTo(LicenseStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        this.status = LicenseStatus.ACTIVE;
        this.userId = teacherId;
        this.activatedAt = now;
    }

    /** 撤销：REVOKED 不能再撤销。 */
    public void revoke(int adminId, String reason, Instant now) {
        if (!status.canTransitionTo(LicenseStatus.REVOKED)) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        this.status = LicenseStatus.REVOKED;
        this.revokedBy = adminId;
        this.revokeReason = reason;
        this.revokedAt = now;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getActivationCodeHash() {
        return activationCodeHash;
    }

    public void setActivationCodeHash(String activationCodeHash) {
        this.activationCodeHash = activationCodeHash;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseStatus status) {
        this.status = status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getCodeHint() {
        return codeHint;
    }

    public void setCodeHint(String codeHint) {
        this.codeHint = codeHint;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(Integer revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
