package com.earlylearning.early_learning_server.teacher;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
