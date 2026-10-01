package com.earlylearning.early_learning_server.admin.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 管理员账号（{@code admin_account}）。password_hash 不出模块、不写日志。 */
@TableName("admin_account")
public class AdminAccount {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String passwordHash;
    private AdminStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AdminStatus getStatus() {
        return status;
    }

    public void setStatus(AdminStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "AdminAccount[id=" + id + ", username=" + username + ", status=" + status + "]";
    }
}
