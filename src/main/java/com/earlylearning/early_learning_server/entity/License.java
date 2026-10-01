package com.earlylearning.early_learning_server.entity;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.enums.LicenseStatus;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 一枚激活码（{@code user_license}）。库里只有哈希，原码只在生成的那次响应里出现。
 *
 * <p>状态只能 UNUSED → ACTIVE（注册占用）、UNUSED / ACTIVE → REVOKED；REVOKED 不可恢复。
 * 迁移规则在这里，落库用 Mapper 里带旧状态条件的 UPDATE，并发时只有一个成功。
 */
@TableName("user_license")
public class License {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private String activationCodeHash;
    private LicenseStatus status;
    private Instant activatedAt;

    /**
     * 注册时确认这枚码可以被占用。
     *
     * @throws BusinessException 已被占用 → 409 LICENSE_UNAVAILABLE；已撤销 → 409 LICENSE_REVOKED
     */
    public void ensureClaimable() {
        if (status == LicenseStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        if (status == LicenseStatus.REVOKED) {
            throw new BusinessException(ErrorCode.LICENSE_REVOKED, HttpStatus.CONFLICT, null, null, null);
        }
    }

    /** 占码：绑定教师并激活。调用前须 {@link #ensureClaimable()}。 */
    public void claimBy(int teacherId, Instant at) {
        ensureClaimable();
        this.userId = teacherId;
        this.status = LicenseStatus.ACTIVE;
        this.activatedAt = at;
    }

    public boolean isActive() {
        return status == LicenseStatus.ACTIVE;
    }

    public boolean isRevoked() {
        return status == LicenseStatus.REVOKED;
    }

    /**
     * 撤销。已撤销时什么也不做。
     *
     * @return 撤销前是否已激活并绑定教师——是则调用方须在同一事务里停用该教师（契约 revokeLicense）
     */
    public boolean revoke() {
        boolean boundTeacherMustBeDisabled = status == LicenseStatus.ACTIVE && userId != null;
        status = LicenseStatus.REVOKED;
        return boundTeacherMustBeDisabled;
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
}
