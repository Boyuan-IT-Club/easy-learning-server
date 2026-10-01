package com.earlylearning.early_learning_server.license.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.license.infrastructure.LicenseMapper;

/**
 * 激活码与教师的绑定：供 auth（注册时锁码、占码）与 teacher、auth（查教师所绑激活码的状态）使用。
 * 写操作要求调用方已开启事务：注册是"锁码 + 建号 + 占码 + 写 refresh 哈希"一个事务。
 */
@Service
public class LicenseBindingService {

    private final LicenseMapper mapper;
    private final KeyedHasher hasher;
    private final Clock clock;

    public LicenseBindingService(LicenseMapper mapper, KeyedHasher hasher, Clock clock) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.clock = clock;
    }

    /** @return 教师所绑激活码的状态；没有绑定时为空 */
    public Optional<LicenseStatus> statusOfUser(int userId) {
        return Optional.ofNullable(mapper.selectByUserId(userId)).map(License::getStatus);
    }

    /**
     * 注册时锁住一枚可用的码，直到调用方事务结束。激活码原样校验（契约），不做任何规范化。
     *
     * @throws BusinessException 不存在或已被使用 → 409 LICENSE_UNAVAILABLE；已撤销 → 409 LICENSE_REVOKED
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public License lockAvailable(String activationCode) {
        License license = mapper.selectByHashForUpdate(hasher.hash(activationCode));
        if (license == null || license.getStatus() == LicenseStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
        if (license.getStatus() == LicenseStatus.REVOKED) {
            throw new BusinessException(ErrorCode.LICENSE_REVOKED, HttpStatus.CONFLICT, null, null, null);
        }
        return license;
    }

    /** 占码：与建号、写 refresh 哈希在同一事务（契约 registerTeacher）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(License license, int userId) {
        if (mapper.markClaimed(license.getId(), userId, clock.instant()) != 1) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE);
        }
    }
}
