package com.earlylearning.early_learning_server.entity;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 教师云端账号（{@code user_account}）。云端不保存教师密码，只保存当前 refresh_token 的哈希。
 */
@TableName("user_account")
public class TeacherAccount {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String refreshTokenHash;
    private TeacherStatus status;
    private Instant createdAt;

    public boolean isEnabled() {
        return status == TeacherStatus.ENABLED;
    }

    /**
     * 能否访问云端（契约 TeacherBearer，鉴权与刷新共用）。
     *
     * @param license 绑定的激活码；没有绑定时为 null
     * @throws BusinessException 停用 → 403 ACCOUNT_DISABLED；激活码不是 ACTIVE → 403 LICENSE_REVOKED
     */
    public void ensureCloudAccess(License license) {
        if (!isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (license == null || !license.isActive()) {
            throw new BusinessException(ErrorCode.LICENSE_REVOKED);
        }
    }

    /**
     * 启用前确认绑定的激活码仍有效（契约 updateTeacherStatus）。
     *
     * @throws BusinessException 激活码不是 ACTIVE → 409 LICENSE_REVOKED（这里是状态冲突，不是鉴权失败）
     */
    public void ensureCanEnable(License license) {
        if (license == null || !license.isActive()) {
            throw new BusinessException(ErrorCode.LICENSE_REVOKED, HttpStatus.CONFLICT, null, null, null);
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public TeacherStatus getStatus() {
        return status;
    }

    public void setStatus(TeacherStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TeacherAccount[id=" + id + ", username=" + username + ", status=" + status + "]";
    }
}
