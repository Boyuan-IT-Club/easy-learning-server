package com.earlylearning.early_learning_server.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.enums.AdminStatus;

import lombok.Getter;
import lombok.Setter;

/** 管理员账号（{@code admin_account}）。password_hash 不出模块、不写日志。 */
@TableName("admin_account")
@Getter
@Setter
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

    @Override
    public String toString() {
        return "AdminAccount[id=" + id + ", username=" + username + ", status=" + status + "]";
    }
}
