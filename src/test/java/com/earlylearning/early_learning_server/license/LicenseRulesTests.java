package com.earlylearning.early_learning_server.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.license.domain.ActivationCode;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;

/** 激活码状态机与格式：纯规则，不需要 Spring。 */
class LicenseRulesTests {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Test
    void 状态只能单向流转() {
        assertThat(LicenseStatus.UNUSED.canTransitionTo(LicenseStatus.ACTIVE)).isTrue();
        assertThat(LicenseStatus.UNUSED.canTransitionTo(LicenseStatus.REVOKED)).isTrue();
        assertThat(LicenseStatus.ACTIVE.canTransitionTo(LicenseStatus.REVOKED)).isTrue();
        assertThat(LicenseStatus.ACTIVE.canTransitionTo(LicenseStatus.UNUSED)).isFalse();
        assertThat(LicenseStatus.REVOKED.canTransitionTo(LicenseStatus.ACTIVE)).isFalse();
        assertThat(LicenseStatus.REVOKED.canTransitionTo(LicenseStatus.UNUSED)).isFalse();
    }

    @Test
    void 已占用的码不能再占用() {
        License license = License.issue("hash", "ABCD", null, 1);
        license.claimBy(10, NOW);
        assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(license.getUserId()).isEqualTo(10);
        assertThatThrownBy(() -> license.claimBy(11, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LICENSE_UNAVAILABLE);
    }

    @Test
    void 撤销后不能再撤销_也不能占用() {
        License license = License.issue("hash", "ABCD", null, 1);
        license.revoke(1, "遗失", NOW);
        assertThat(license.getRevokeReason()).isEqualTo("遗失");
        assertThatThrownBy(() -> license.revoke(1, "再次", NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> license.claimBy(10, NOW)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 激活码是16位_展示时分4组_尾号取末4位() {
        ActivationCode code = ActivationCode.generate();
        assertThat(code.value()).hasSize(16);
        assertThat(code.formatted()).matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}");
        assertThat(code.hint()).isEqualTo(code.value().substring(12));
        assertThat(code.toString()).doesNotContain(code.value());
        assertThat(ActivationCode.parse(code.formatted().toLowerCase())).contains(code);
    }
}
