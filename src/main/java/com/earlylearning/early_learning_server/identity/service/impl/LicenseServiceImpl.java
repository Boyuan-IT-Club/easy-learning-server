package com.earlylearning.early_learning_server.identity.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.License;
import com.earlylearning.early_learning_server.entity.LicenseStatus;
import com.earlylearning.early_learning_server.entity.TeacherStatus;
import com.earlylearning.early_learning_server.identity.dto.CreateLicensesResponse;
import com.earlylearning.early_learning_server.identity.dto.IssuedLicenseResponse;
import com.earlylearning.early_learning_server.identity.dto.LicenseResponse;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.identity.model.ActivationCodes;
import com.earlylearning.early_learning_server.identity.model.IssuedLicenseIds;
import com.earlylearning.early_learning_server.identity.service.LicenseService;

/** {@link LicenseService} 的实现。 */
@Service
public class LicenseServiceImpl implements LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseServiceImpl.class);

    /** 单次最多生成的激活码数量。 */
    private static final int MAX_BATCH = 100;

    /** 80 bit 的码撞上已有哈希的概率可以忽略；重试只是为了不让极小概率变成 500。 */
    private static final int MAX_COLLISION_RETRIES = 3;

    private final LicenseMapper licenseMapper;
    private final TeacherAccountMapper teacherAccountMapper;
    private final KeyedHasher keyedHasher;
    private final SensitiveIdempotency sensitiveIdempotency;

    public LicenseServiceImpl(LicenseMapper licenseMapper,
                              TeacherAccountMapper teacherAccountMapper,
                              KeyedHasher keyedHasher,
                              SensitiveIdempotency sensitiveIdempotency) {
        this.licenseMapper = licenseMapper;
        this.teacherAccountMapper = teacherAccountMapper;
        this.keyedHasher = keyedHasher;
        this.sensitiveIdempotency = sensitiveIdempotency;
    }

    @Override
    public CreateLicensesResponse create(Integer count, int adminId, String idempotencyKey) {
        if (count == null || count < 1 || count > MAX_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/count"));
        }
        String fingerprint = InputFingerprint.of(Integer.toString(count), Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.LICENSE_CREATE, idempotencyKey, fingerprint, 201,
                CreateLicensesResponse.class, () -> issue(count, adminId),
                IssuedLicenseIds.class, CreateLicensesResponse::ids,
                LicenseServiceImpl::expiredDetails, result -> { });
    }

    @Override
    public PageResponse<LicenseResponse> list(PageQuery page, LicenseStatus status, Integer userId) {
        String statusValue = status == null ? null : status.name();
        return PageResponse.of(page, licenseMapper.selectPage(statusValue, userId, page.pageSize(), page.offset()),
                licenseMapper.countMatching(statusValue, userId), LicenseResponse::from);
    }

    @Override
    @Transactional
    public LicenseResponse revoke(int id) {
        License license = licenseMapper.selectForUpdate(id);
        if (license == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (license.isRevoked()) {
            log.info("激活码已是撤销状态，直接返回 licenseId={}", id);
            return LicenseResponse.from(license);
        }
        boolean disableTeacher = license.revoke();
        if (licenseMapper.markRevoked(id) != 1) {
            throw new IllegalStateException("撤销时激活码状态被并发修改 id=" + id);
        }
        if (disableTeacher) {
            teacherAccountMapper.updateStatus(license.getUserId(), TeacherStatus.DISABLED.value());
        }
        log.info("撤销激活码 licenseId={} disabledUserId={}", id, disableTeacher ? license.getUserId() : null);
        return LicenseResponse.from(license);
    }

    private CreateLicensesResponse issue(int count, int adminId) {
        List<IssuedLicenseResponse> issued = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            issued.add(insertUnique());
        }
        // 只记 id，激活码原文不进日志
        log.info("生成激活码 adminId={} count={} firstId={} lastId={}",
                adminId, count, issued.getFirst().id(), issued.getLast().id());
        return new CreateLicensesResponse(issued);
    }

    private IssuedLicenseResponse insertUnique() {
        for (int attempt = 0; ; attempt++) {
            String code = ActivationCodes.generate();
            License license = new License();
            license.setActivationCodeHash(keyedHasher.hash(code));
            license.setStatus(LicenseStatus.UNUSED);
            try {
                licenseMapper.insert(license);
                return IssuedLicenseResponse.unused(license.getId(), code);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_COLLISION_RETRIES) {
                    throw e;
                }
                log.warn("激活码哈希与已有记录冲突，重新生成 attempt={}", attempt + 1);
            }
        }
    }

    private static ApiErrorDetails expiredDetails(IssuedLicenseIds snapshot) {
        List<Long> ids = snapshot.ids().stream().map(Integer::longValue).toList();
        return ids.isEmpty() ? null : new ApiErrorDetails(null, null, null, null, null, ids, null);
    }
}
