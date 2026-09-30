package com.earlylearning.early_learning_server.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.Username;

/** 教师账号的"能不能用"与恢复码规则：纯规则，不需要 Spring。 */
class TeacherAccountRulesTests {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final DeviceId DEVICE = DeviceId.parse(UUID.randomUUID().toString()).orElseThrow();
    private static final Optional<LicenseStatus> ACTIVE = Optional.of(LicenseStatus.ACTIVE);

    private TeacherAccount account() {
        return TeacherAccount.register(Username.parse("teacher_1"), DEVICE, NOW);
    }

    @Test
    void 正常账号在绑定设备上可用() {
        assertThat(account().checkUsable(Optional.of(DEVICE), ACTIVE)).isEmpty();
    }

    @Test
    void 按停用_撤销_设备的顺序给出第一个原因() {
        TeacherAccount account = account();
        Optional<DeviceId> other = DeviceId.parse(UUID.randomUUID().toString());
        assertThat(account.checkUsable(other, ACTIVE)).contains(ErrorCode.DEVICE_MISMATCH);
        assertThat(account.checkUsable(Optional.empty(), ACTIVE)).contains(ErrorCode.DEVICE_MISMATCH);
        assertThat(account.checkUsable(Optional.of(DEVICE), Optional.of(LicenseStatus.REVOKED)))
                .contains(ErrorCode.LICENSE_REVOKED);
        assertThat(account.checkUsable(Optional.of(DEVICE), Optional.empty())).contains(ErrorCode.LICENSE_REVOKED);
        account.disable("测试");
        assertThat(account.checkUsable(Optional.of(DEVICE), Optional.of(LicenseStatus.REVOKED)))
                .contains(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void 解绑后原设备不可用_且refresh作废() {
        TeacherAccount account = account();
        account.rotateRefresh("hash");
        account.unbindDevice();
        assertThat(account.isDeviceBound()).isFalse();
        assertThat(account.getRefreshTokenHash()).isNull();
        assertThat(account.checkUsable(Optional.of(DEVICE), ACTIVE)).contains(ErrorCode.DEVICE_MISMATCH);
    }

    @Test
    void 恢复码_过期或哈希不符都无效() {
        TeacherAccount account = account();
        assertThat(account.recoveryCodeMatches("h", NOW)).isFalse();
        account.issueRecoveryCode("h", NOW.plus(Duration.ofHours(24)));
        assertThat(account.recoveryCodeMatches("h", NOW)).isTrue();
        assertThat(account.recoveryCodeMatches("x", NOW)).isFalse();
        assertThat(account.recoveryCodeMatches("h", NOW.plus(Duration.ofHours(24)))).isFalse();
    }

    @Test
    void 恢复码错到上限即作废() {
        TeacherAccount account = account();
        account.issueRecoveryCode("h", NOW.plus(Duration.ofHours(24)));
        for (int i = 1; i < 5; i++) {
            assertThat(account.recordRecoveryFailure(5)).isFalse();
        }
        assertThat(account.recordRecoveryFailure(5)).isTrue();
        assertThat(account.recoveryCodeMatches("h", NOW)).isFalse();
    }

    @Test
    void 重新签发清零错误计数() {
        TeacherAccount account = account();
        account.issueRecoveryCode("h", NOW.plus(Duration.ofHours(24)));
        account.recordRecoveryFailure(5);
        account.issueRecoveryCode("h2", NOW.plus(Duration.ofHours(24)));
        assertThat(account.getRecoveryCodeFailedCount()).isZero();
    }

    @Test
    void 用户名格式() {
        assertThat(Username.parse("Zhang.Li-01").value()).isEqualTo("Zhang.Li-01");
        assertThatThrownBy(() -> Username.parse("abc")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Username.parse("含中文的名字")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Username.parse(null)).isInstanceOf(BusinessException.class);
    }
}
