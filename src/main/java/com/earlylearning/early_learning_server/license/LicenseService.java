package com.earlylearning.early_learning_server.license;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.common.secret.RandomCodes;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageParams;
import com.earlylearning.early_learning_server.license.web.IssuedLicenseResponse;
import com.earlylearning.early_learning_server.license.web.LicensePageResponse;
import com.earlylearning.early_learning_server.license.web.LicenseResponse;
import com.earlylearning.early_learning_server.teacher.TeacherAccountService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 激活码管理（契约 createLicenses、listLicenses、revokeLicense）。
 */
@Service
public class LicenseService {

    /** PRD 2.2-1：16 位激活码。 */
    static final int CODE_LENGTH = 16;
    static final int MAX_BATCH = 100;
    /** 16 位 Crockford 码约 80 bit，撞上已有哈希的概率可以忽略；重试只是为了不让极小概率变成 500。 */
    private static final int MAX_COLLISION_RETRIES = 3;

    private final LicenseMapper mapper;
    private final KeyedHasher hasher;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final TeacherAccountService teachers;
    private final ObjectMapper objectMapper;

    public LicenseService(LicenseMapper mapper,
                          KeyedHasher hasher,
                          SensitiveIdempotency sensitiveIdempotency,
                          TeacherAccountService teachers,
                          ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.teachers = teachers;
        this.objectMapper = objectMapper;
    }

    /**
     * 单个或批量生成，整批一个事务。原码只在首次响应与短时重放中出现；
     * 重放过期返回 409 SENSITIVE_RESULT_EXPIRED，details.license_ids 给出这次实际创建的全部 id。
     */
    public StoredResponse create(Integer count, int adminId, String idempotencyKey) {
        if (count == null || count < 1 || count > MAX_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/count"));
        }
        String fingerprint = InputFingerprint.of(Integer.toString(count), Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.LICENSE_CREATE, idempotencyKey, fingerprint, 201,
                () -> issue(count),
                issued -> ApiResponse.ok(Map.of("items", issued)),
                issued -> ApiResponse.ok(Map.of("license_ids", issued.stream().map(IssuedLicenseResponse::id).toList())),
                this::expiredDetails,
                body -> { });
    }

    public LicensePageResponse list(PageParams page, LicenseStatus status, Integer userId) {
        String statusValue = status == null ? null : status.name();
        List<License> items = mapper.selectPage(statusValue, userId, page.pageSize(), page.offset());
        long total = mapper.countMatching(statusValue, userId);
        return new LicensePageResponse(items.stream().map(LicenseResponse::from).toList(),
                page.page(), page.pageSize(), total);
    }

    /**
     * UNUSED / ACTIVE 均可撤销；撤销 ACTIVE 与禁用绑定教师同一事务提交；已 REVOKED 幂等返回。
     * 不可恢复，不影响本地离线业务。
     */
    @Transactional
    public LicenseResponse revoke(int id) {
        License license = mapper.selectForUpdate(id);
        if (license == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (license.getStatus() == LicenseStatus.REVOKED) {
            return LicenseResponse.from(license);
        }
        if (mapper.markRevoked(id) != 1) {
            throw new IllegalStateException("撤销时激活码状态被并发修改 id=" + id);
        }
        if (license.getStatus() == LicenseStatus.ACTIVE && license.getUserId() != null) {
            teachers.disable(license.getUserId());
        }
        license.setStatus(LicenseStatus.REVOKED);
        return LicenseResponse.from(license);
    }

    private List<IssuedLicenseResponse> issue(int count) {
        List<IssuedLicenseResponse> issued = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            issued.add(insertUnique());
        }
        return issued;
    }

    private IssuedLicenseResponse insertUnique() {
        for (int attempt = 0; ; attempt++) {
            String code = RandomCodes.generate(CODE_LENGTH);
            License license = new License();
            license.setActivationCodeHash(hasher.hash(code));
            license.setStatus(LicenseStatus.UNUSED);
            try {
                mapper.insert(license);
                return new IssuedLicenseResponse(license.getId(), code, LicenseStatus.UNUSED.name());
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_COLLISION_RETRIES) {
                    throw e;
                }
            }
        }
    }

    /** 从脱敏包络 {@code {data: {license_ids: [...]}}} 取回这次创建的 id。 */
    private ApiErrorDetails expiredDetails(String snapshotJson) {
        JsonNode ids = objectMapper.readTree(snapshotJson).path("data").path("license_ids");
        List<Long> licenseIds = new ArrayList<>();
        ids.forEach(node -> licenseIds.add(node.asLong()));
        return licenseIds.isEmpty() ? null : new ApiErrorDetails(null, null, null, null, null, licenseIds, null);
    }
}
