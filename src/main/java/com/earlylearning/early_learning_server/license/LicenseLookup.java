package com.earlylearning.early_learning_server.license;

import java.time.Clock;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;

/**
 * 供 auth、teacher 使用的激活码能力：查教师所绑激活码的状态、注册时锁码与占码。
 *
 * <p>只依赖 user_license 本身，不依赖 teacher——这样 license 撤销时调用 teacher 禁用账号、
 * teacher 启用时调用这里查激活码，两边不会形成 Bean 循环依赖。
 */
@Component
public class LicenseLookup {

    private final LicenseMapper mapper;
    private final KeyedHasher hasher;
    private final Clock clock;

    public LicenseLookup(LicenseMapper mapper, KeyedHasher hasher, Clock clock) {
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
