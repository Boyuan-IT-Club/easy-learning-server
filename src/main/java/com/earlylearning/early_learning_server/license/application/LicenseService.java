package com.earlylearning.early_learning_server.license.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.paging.PageResult;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.license.domain.ActivationCodes;
import com.earlylearning.early_learning_server.license.domain.IssuedLicense;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.license.domain.LicenseBatch;
import com.earlylearning.early_learning_server.license.domain.LicenseBatchIds;
import com.earlylearning.early_learning_server.license.domain.LicenseRevoked;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.license.domain.LicenseSummary;
import com.earlylearning.early_learning_server.license.infrastructure.LicenseMapper;

/** 管理端激活码用例（契约 createLicenses、listLicenses、revokeLicense）。 */
@Service
public class LicenseService {

    public static final int MAX_BATCH = 100;
    /** 80 bit 的码撞上已有哈希的概率可以忽略；重试只是为了不让极小概率变成 500。 */
    private static final int MAX_COLLISION_RETRIES = 3;

    private final LicenseMapper mapper;
    private final KeyedHasher hasher;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final ApplicationEventPublisher events;

    public LicenseService(LicenseMapper mapper,
                          KeyedHasher hasher,
                          SensitiveIdempotency sensitiveIdempotency,
                          ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.events = events;
    }

    /**
     * 单个或批量生成，整批一个事务。原码只在首次结果与短时重放中出现；
     * 重放过期返回 409 SENSITIVE_RESULT_EXPIRED，details.license_ids 给出这次实际创建的全部 id。
     */
    public LicenseBatch create(Integer count, int adminId, String idempotencyKey) {
        if (count == null || count < 1 || count > MAX_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/count"));
        }
        String fingerprint = InputFingerprint.of(Integer.toString(count), Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.LICENSE_CREATE, idempotencyKey, fingerprint, 201,
                LicenseBatch.class, () -> issue(count),
                LicenseBatchIds.class, LicenseBatch::ids,
                LicenseService::expiredDetails, batch -> { });
    }

    public PageResult<LicenseSummary> list(PageQuery page, LicenseStatus status, Integer userId) {
        String statusValue = status == null ? null : status.name();
        List<License> items = mapper.selectPage(statusValue, userId, page.pageSize(), page.offset());
        long total = mapper.countMatching(statusValue, userId);
        return PageResult.of(page, items, total).map(LicenseSummary::of);
    }

    /**
     * UNUSED / ACTIVE 均可撤销；已 REVOKED 幂等返回。撤销已激活的码时发布 {@link LicenseRevoked}，
     * teacher 模块在同一事务里停用绑定教师。不可恢复，不影响本地离线业务。
     */
    @Transactional
    public LicenseSummary revoke(int id) {
        License license = mapper.selectForUpdate(id);
        if (license == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (license.getStatus() == LicenseStatus.REVOKED) {
            return LicenseSummary.of(license);
        }
        if (mapper.markRevoked(id) != 1) {
            throw new IllegalStateException("撤销时激活码状态被并发修改 id=" + id);
        }
        if (license.getStatus() == LicenseStatus.ACTIVE && license.getUserId() != null) {
            events.publishEvent(new LicenseRevoked(id, license.getUserId()));
        }
        license.setStatus(LicenseStatus.REVOKED);
        return LicenseSummary.of(license);
    }

    private LicenseBatch issue(int count) {
        List<IssuedLicense> issued = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            issued.add(insertUnique());
        }
        return new LicenseBatch(issued);
    }

    private IssuedLicense insertUnique() {
        for (int attempt = 0; ; attempt++) {
            String code = ActivationCodes.generate();
            License license = new License();
            license.setActivationCodeHash(hasher.hash(code));
            license.setStatus(LicenseStatus.UNUSED);
            try {
                mapper.insert(license);
                return new IssuedLicense(license.getId(), code);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_COLLISION_RETRIES) {
                    throw e;
                }
            }
        }
    }

    private static ApiErrorDetails expiredDetails(LicenseBatchIds snapshot) {
        List<Long> ids = snapshot.ids().stream().map(Integer::longValue).toList();
        return ids.isEmpty() ? null : new ApiErrorDetails(null, null, null, null, null, ids, null);
    }
}
