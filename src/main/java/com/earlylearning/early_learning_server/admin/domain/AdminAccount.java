package com.earlylearning.early_learning_server.admin.domain;

import java.time.Instant;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 管理员账号（{@code admin_account}）。
 *
 * <p>密码哈希只在本模块内部流转，不进任何响应、快照或日志（{@link #toString()} 已脱敏）。
 */
@TableName("admin_account")
public class AdminAccount {

    /** 与教师用户名同一套规则，区分大小写（V1 排序规则为 utf8mb4_0900_bin）。 */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{4,32}$");
    /** BCrypt 只取前 72 字节，更长的部分不参与校验，所以直接拒绝。 */
    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD = 72;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String passwordHash;
    private AdminStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static AdminAccount create(String username, String passwordHash) {
        AdminAccount account = new AdminAccount();
        account.username = username;
        account.passwordHash = passwordHash;
        account.status = AdminStatus.ACTIVE;
        return account;
    }

    /** @throws BusinessException 400，details.field_path 为给定字段 */
    public static void checkUsername(String username, String fieldPath) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField(fieldPath));
        }
    }

    /** @throws BusinessException 400，details.field_path 为给定字段 */
    public static void checkPasswordPolicy(String password, String fieldPath) {
        if (password == null || password.length() < MIN_PASSWORD
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "管理员密码长度需为 10–72 字节",
                    ApiErrorDetails.atField(fieldPath));
        }
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
    }

    /** @throws BusinessException 403 ACCOUNT_DISABLED */
    public void ensureActive() {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
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
