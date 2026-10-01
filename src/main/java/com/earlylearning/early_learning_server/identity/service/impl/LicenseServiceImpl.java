package com.earlylearning.early_learning_server.identity.service.impl;

import java.util.ArrayList;
import java.util.List;

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

    /** 单次最多生成的激活码数量。 */
    public static final int MAX_BATCH = 100;

    /** 80 bit 的码撞上已有哈希的概率可以忽略；重试只是为了不让极小概率变成 500。 */
    private static final int MAX_COLLISION_RETRIES = 3;

    private final LicenseMapper licenses;
    private final TeacherAccountMapper teachers;
    private final KeyedHasher hasher;
    private final SensitiveIdempotency sensitiveIdempotency;

    public LicenseServiceImpl(LicenseMapper licenses,
                              TeacherAccountMapper teachers,
                              KeyedHasher hasher,
                              SensitiveIdempotency sensitiveIdempotency) {
        this.licenses = licenses;
        this.teachers = teachers;
        this.hasher = hasher;
        this.sensitiveIdempotency = sensitiveIdempotency;
    }

    @Override
    public CreateLicensesResponse create(Integer count, int adminId, String idempotencyKey) {
        if (count == null || count < 1 || count > MAX_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/count"));
        }
        String fingerprint = InputFingerprint.of(Integer.toString(count), Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.LICENSE_CREATE, idempotencyKey, fingerprint, 201,
                CreateLicensesResponse.class, () -> issue(count),
                IssuedLicenseIds.class, CreateLicensesResponse::ids,
                LicenseServiceImpl::expiredDetails, result -> { });
    }

    @Override
    public PageResponse<LicenseResponse> list(PageQuery page, LicenseStatus status, Integer userId) {
        String statusValue = status == null ? null : status.name();
        return PageResponse.of(page, licenses.selectPage(statusValue, userId, page.pageSize(), page.offset()),
                licenses.countMatching(statusValue, userId), LicenseResponse::from);
    }

    @Override
    @Transactional
    public LicenseResponse revoke(int id) {
        License license = licenses.selectForUpdate(id);
        if (license == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (license.isRevoked()) {
            return LicenseResponse.from(license);
        }
        boolean disableTeacher = license.revoke();
        if (licenses.markRevoked(id) != 1) {
            throw new IllegalStateException("撤销时激活码状态被并发修改 id=" + id);
        }
        if (disableTeacher) {
            teachers.updateStatus(license.getUserId(), TeacherStatus.DISABLED.value());
        }
        return LicenseResponse.from(license);
    }

    private CreateLicensesResponse issue(int count) {
        List<IssuedLicenseResponse> issued = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            issued.add(insertUnique());
        }
        return new CreateLicensesResponse(issued);
    }

    private IssuedLicenseResponse insertUnique() {
        for (int attempt = 0; ; attempt++) {
            String code = ActivationCodes.generate();
            License license = new License();
            license.setActivationCodeHash(hasher.hash(code));
            license.setStatus(LicenseStatus.UNUSED);
            try {
                licenses.insert(license);
                return IssuedLicenseResponse.unused(license.getId(), code);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_COLLISION_RETRIES) {
                    throw e;
                }
            }
        }
    }

    private static ApiErrorDetails expiredDetails(IssuedLicenseIds snapshot) {
        List<Long> ids = snapshot.ids().stream().map(Integer::longValue).toList();
        return ids.isEmpty() ? null : new ApiErrorDetails(null, null, null, null, null, ids, null);
    }
}
