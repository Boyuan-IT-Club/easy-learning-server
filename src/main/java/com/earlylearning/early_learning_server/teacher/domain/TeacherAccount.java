package com.earlylearning.early_learning_server.teacher.domain;

import java.time.Instant;
import java.util.Optional;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;

/**
 * 教师云端账号（{@code user_account}）。云端不保存教师密码。
 *
 * <p>"账号能不能用"只在这里判断一次（{@link #checkUsable}），认证、刷新、恢复三处共用；
 * 恢复码的有效性与错误计数也在这里（{@link #recoveryCodeMatches}、{@link #recordRecoveryFailure}）。
 * setter 留给 MyBatis 映射使用。
 */
@TableName("user_account")
public class TeacherAccount {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String refreshTokenHash;
    private TeacherStatus status;
    private Instant createdAt;
    private String deviceId;
    private Instant deviceBoundAt;
    private Instant lastRefreshAt;
    private String recoveryCodeHash;
    private Instant recoveryCodeExpiresAt;
    private Integer recoveryCodeFailedCount;
    private String disabledReason;
    private Instant updatedAt;

    /** 注册：新账号启用，并绑定注册所用的设备（PRD 2.2-3）。 */
    public static TeacherAccount register(Username username, DeviceId device, Instant now) {
        TeacherAccount account = new TeacherAccount();
        account.username = username.value();
        account.status = TeacherStatus.ENABLED;
        account.deviceId = device.value();
        account.deviceBoundAt = now;
        account.recoveryCodeFailedCount = 0;
        return account;
    }

    /**
     * 这个账号此刻能否从这台设备访问云端。
     *
     * @param requestDevice 请求所在的设备；为空表示请求没带合法的设备号
     * @param license       所绑激活码的状态；为空表示没有绑定
     * @return 不可用时给出原因：ACCOUNT_DISABLED、LICENSE_REVOKED、DEVICE_MISMATCH
     */
    public Optional<ErrorCode> checkUsable(Optional<DeviceId> requestDevice, Optional<LicenseStatus> license) {
        return checkStatus(license).or(() -> isBoundTo(requestDevice)
                ? Optional.empty()
                : Optional.of(ErrorCode.DEVICE_MISMATCH));
    }

    /** 只看账号与激活码，不看设备。恢复流程用：恢复本身就可能是换设备。 */
    public Optional<ErrorCode> checkStatus(Optional<LicenseStatus> license) {
        if (status != TeacherStatus.ENABLED) {
            return Optional.of(ErrorCode.ACCOUNT_DISABLED);
        }
        if (license.isEmpty() || license.get() != LicenseStatus.ACTIVE) {
            return Optional.of(ErrorCode.LICENSE_REVOKED);
        }
        return Optional.empty();
    }

    public boolean isBoundTo(Optional<DeviceId> device) {
        return deviceId != null && device.isPresent() && deviceId.equals(device.get().value());
    }

    public boolean isDeviceBound() {
        return deviceId != null;
    }

    public void disable(String reason) {
        this.status = TeacherStatus.DISABLED;
        this.disabledReason = reason;
    }

    public void enable() {
        this.status = TeacherStatus.ENABLED;
    }

    /** 解绑：同时作废 refresh_token，原设备再也刷新不了（PRD 2.2-7 受控解绑）。 */
    public void unbindDevice() {
        this.deviceId = null;
        this.deviceBoundAt = null;
        this.refreshTokenHash = null;
    }

    public void bindDevice(DeviceId device, Instant now) {
        this.deviceId = device.value();
        this.deviceBoundAt = now;
    }

    public void rotateRefresh(String newHash) {
        this.refreshTokenHash = newHash;
    }

    /** 签发新恢复码：覆盖旧码并清零错误计数。 */
    public void issueRecoveryCode(String hash, Instant expiresAt) {
        this.recoveryCodeHash = hash;
        this.recoveryCodeExpiresAt = expiresAt;
        this.recoveryCodeFailedCount = 0;
    }

    /** 恢复码有效：已签发、未过期、哈希一致。 */
    public boolean recoveryCodeMatches(String hash, Instant now) {
        return recoveryCodeHash != null
                && recoveryCodeExpiresAt != null
                && now.isBefore(recoveryCodeExpiresAt)
                && recoveryCodeHash.equals(hash);
    }

    /**
     * 记一次恢复码错误。累计达到上限时恢复码作废，之后即使输对也无效，需要管理员重新签发。
     *
     * @return 本次之后恢复码是否已作废
     */
    public boolean recordRecoveryFailure(int maxFailures) {
        if (recoveryCodeHash == null) {
            return true;
        }
        int failures = (recoveryCodeFailedCount == null ? 0 : recoveryCodeFailedCount) + 1;
        this.recoveryCodeFailedCount = failures;
        if (failures >= maxFailures) {
            clearRecoveryCode();
            this.recoveryCodeFailedCount = failures;
            return true;
        }
        return false;
    }

    public void clearRecoveryCode() {
        this.recoveryCodeHash = null;
        this.recoveryCodeExpiresAt = null;
        this.recoveryCodeFailedCount = 0;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Instant getDeviceBoundAt() {
        return deviceBoundAt;
    }

    public void setDeviceBoundAt(Instant deviceBoundAt) {
        this.deviceBoundAt = deviceBoundAt;
    }

    public Instant getLastRefreshAt() {
        return lastRefreshAt;
    }

    public void setLastRefreshAt(Instant lastRefreshAt) {
        this.lastRefreshAt = lastRefreshAt;
    }

    public String getRecoveryCodeHash() {
        return recoveryCodeHash;
    }

    public void setRecoveryCodeHash(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
    }

    public Instant getRecoveryCodeExpiresAt() {
        return recoveryCodeExpiresAt;
    }

    public void setRecoveryCodeExpiresAt(Instant recoveryCodeExpiresAt) {
        this.recoveryCodeExpiresAt = recoveryCodeExpiresAt;
    }

    public Integer getRecoveryCodeFailedCount() {
        return recoveryCodeFailedCount;
    }

    public void setRecoveryCodeFailedCount(Integer recoveryCodeFailedCount) {
        this.recoveryCodeFailedCount = recoveryCodeFailedCount;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    public void setDisabledReason(String disabledReason) {
        this.disabledReason = disabledReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "TeacherAccount[id=" + id + ", username=" + username + ", status=" + status + "]";
    }
}
