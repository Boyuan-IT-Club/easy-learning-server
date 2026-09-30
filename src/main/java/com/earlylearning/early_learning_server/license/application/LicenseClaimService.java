package com.earlylearning.early_learning_server.license.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.license.domain.ActivationCode;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.license.infrastructure.LicenseMapper;

/**
 * 供 teacher 模块使用的激活码能力：校验可用、注册时锁码与占码、查询教师所绑激活码的状态。
 *
 * <p>占码规则（只有 UNUSED 可以占用）留在 License 聚合里，teacher 不直接碰 user_license 表。
 */
@Service
public class LicenseClaimService {

    private final LicenseMapper mapper;
    private final KeyedHasher hasher;
    private final Clock clock;

    public LicenseClaimService(LicenseMapper mapper, KeyedHasher hasher, Clock clock) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.clock = clock;
    }

    /** 只读校验。不存在、已使用、已撤销一律不可用，不区分原因，防止探测。 */
    public boolean isAvailable(ActivationCode code) {
        License license = mapper.selectByHash(hasher.hash(code.value()));
        return license != null && license.getStatus() == LicenseStatus.UNUSED;
    }

    /**
     * 锁住一枚可用的码，直到调用方事务结束。
     *
     * @throws BusinessException 409 LICENSE_UNAVAILABLE
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public License lockAvailable(ActivationCode code) {
        License license = mapper.selectByHashForUpdate(hasher.hash(code.value()));
        if (license == null || license.getStatus() != LicenseStatus.UNUSED) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        return license;
    }

    /** 占码：与建号在同一事务里（PRD 2.2-3 原子完成）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(License license, int teacherId) {
        license.claimBy(teacherId, clock.instant());
        if (mapper.markClaimed(license.getId(), teacherId, license.getActivatedAt()) != 1) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
    }

    /** @return 教师所绑激活码的状态；没有绑定时为空 */
    public Optional<LicenseStatus> statusOfTeacher(int teacherId) {
        return Optional.ofNullable(mapper.selectByUserId(teacherId)).map(License::getStatus);
    }
}
