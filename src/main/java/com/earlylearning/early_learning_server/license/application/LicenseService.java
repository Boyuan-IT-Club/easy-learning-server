package com.earlylearning.early_learning_server.license.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.TargetType;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.SensitiveIdempotency;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.license.domain.ActivationCode;
import com.earlylearning.early_learning_server.license.domain.License;
import com.earlylearning.early_learning_server.license.domain.LicenseBatch;
import com.earlylearning.early_learning_server.license.domain.LicensePage;
import com.earlylearning.early_learning_server.license.domain.LicenseStatus;
import com.earlylearning.early_learning_server.license.domain.LicenseSummary;
import com.earlylearning.early_learning_server.license.infrastructure.LicenseMapper;
import com.earlylearning.early_learning_server.license.infrastructure.LicenseQueryMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 管理端的激活码操作：批量生成、列表、按码查询、撤销。 */
@Service
public class LicenseService {

    static final int MAX_BATCH = 200;
    static final int MAX_REMARK = 100;
    static final int MAX_REASON = 200;
    private static final int MAX_PAGE_SIZE = 100;
    /** 16 位码的 HMAC 撞上已有记录的概率可以忽略；重试几次只是为了不把这种极小概率变成一次 500。 */
    private static final int MAX_COLLISION_RETRIES = 3;

    private final LicenseMapper mapper;
    private final LicenseQueryMapper queryMapper;
    private final KeyedHasher hasher;
    private final SensitiveIdempotency sensitiveIdempotency;
    private final AuditLogService audit;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LicenseService(LicenseMapper mapper,
                          LicenseQueryMapper queryMapper,
                          KeyedHasher hasher,
                          SensitiveIdempotency sensitiveIdempotency,
                          AuditLogService audit,
                          TokenService tokenService,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.mapper = mapper;
        this.queryMapper = queryMapper;
        this.hasher = hasher;
        this.sensitiveIdempotency = sensitiveIdempotency;
        this.audit = audit;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 批量生成。明文只在本次结果里出现；同一个 Idempotency-Key 在 10 分钟内重放返回同一批，
     * 之后返回 SENSITIVE_RESULT_EXPIRED，并在 details.license_ids 里给出这批码的 id，便于整批撤销。
     */
    public LicenseBatch issueBatch(int count, String remark, int adminId, String idempotencyKey) {
        if (count < 1 || count > MAX_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/count"));
        }
        String normalizedRemark = blankToNull(remark);
        if (normalizedRemark != null && normalizedRemark.length() > MAX_REMARK) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/remark"));
        }
        String fingerprint = InputFingerprint.of(Integer.toString(count), normalizedRemark, Integer.toString(adminId));
        return sensitiveIdempotency.execute(IdempotencyScope.LICENSE_BATCH_CREATE, idempotencyKey, fingerprint,
                201, LicenseBatch.class,
                () -> createBatch(count, normalizedRemark, adminId),
                LicenseBatch::redacted,
                this::expiredDetails);
    }

    public LicensePage page(LicenseStatus status, String keyword, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 1);
        String statusValue = status == null ? null : status.name();
        String pattern = likePattern(keyword);
        List<LicenseSummary> items = queryMapper.selectPage(statusValue, pattern, pageSize,
                (long) (pageNo - 1) * pageSize);
        long total = queryMapper.countMatching(statusValue, pattern);
        Map<LicenseStatus, Long> counts = new EnumMap<>(LicenseStatus.class);
        for (LicenseStatus value : LicenseStatus.values()) {
            counts.put(value, 0L);
        }
        for (LicenseQueryMapper.StatusCount row : queryMapper.countByStatus()) {
            counts.put(LicenseStatus.valueOf(row.status()), row.total());
        }
        return new LicensePage(items, total, pageNo, pageSize, counts);
    }

    /** 按完整码查询。用 POST 传码，完整码不会出现在 URL 与访问日志里。 */
    public LicenseSummary lookup(String rawCode) {
        ActivationCode code = ActivationCode.parse(rawCode).orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/activation_code")));
        License license = mapper.selectByHash(hasher.hash(code.value()));
        if (license == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return queryMapper.selectById(license.getId());
    }

    /**
     * 撤销：全部成功或全部失败。撤销 ACTIVE 码会在提交后吊销对应教师的全部 access_token。
     *
     * @throws BusinessException 409 LICENSE_UNAVAILABLE，details.license_ids 列出不存在或已撤销的 id
     */
    @Transactional
    public void revoke(List<Integer> licenseIds, String reason, int adminId) {
        if (licenseIds == null || licenseIds.isEmpty() || licenseIds.size() > MAX_BATCH
                || licenseIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(licenseIds).size() != licenseIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/license_ids"));
        }
        String normalizedReason = requireReason(reason);
        List<License> locked = mapper.selectByIdsForUpdate(licenseIds);
        Set<Integer> found = new HashSet<>();
        List<Long> unavailable = new ArrayList<>();
        for (License license : locked) {
            found.add(license.getId());
            if (!license.getStatus().canTransitionTo(LicenseStatus.REVOKED)) {
                unavailable.add(license.getId().longValue());
            }
        }
        for (Integer id : licenseIds) {
            if (!found.contains(id)) {
                unavailable.add(id.longValue());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new BusinessException(ErrorCode.LICENSE_UNAVAILABLE, licenseIdsDetails(unavailable));
        }

        Instant now = clock.instant();
        for (License license : locked) {
            boolean wasActive = license.getStatus() == LicenseStatus.ACTIVE;
            license.revoke(adminId, normalizedReason, now);
            if (mapper.markRevoked(license.getId(), adminId, normalizedReason, now) != 1) {
                throw new IllegalStateException("撤销时激活码状态被并发修改 id=" + license.getId());
            }
            if (wasActive && license.getUserId() != null) {
                tokenService.revokeTeacherAfterCommit(license.getUserId());
            }
            audit.record(AuditEntry.byAdmin(adminId, AuditAction.LICENSE_REVOKED, TargetType.LICENSE, license.getId())
                    .withReason(normalizedReason)
                    .withDetail(Map.of("previous_status", wasActive ? "ACTIVE" : "UNUSED")));
        }
    }

    private LicenseBatch createBatch(int count, String remark, int adminId) {
        List<Inserted> inserted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            inserted.add(insertUnique(remark, adminId));
        }
        List<Integer> ids = inserted.stream().map(row -> row.license().getId()).toList();
        // 回读一次，拿到数据库生成的 created_at
        Map<Integer, License> saved = new HashMap<>();
        mapper.selectByIds(ids).forEach(license -> saved.put(license.getId(), license));
        List<LicenseBatch.Issued> issued = inserted.stream().map(row -> {
            License license = saved.get(row.license().getId());
            return new LicenseBatch.Issued(license.getId(), row.code().formatted(), license.getCodeHint(),
                    license.getStatus(), license.getRemark(), license.getCreatedAt());
        }).toList();
        audit.record(AuditEntry.byAdmin(adminId, AuditAction.LICENSE_BATCH_CREATED, TargetType.LICENSE, ids.getFirst())
                .withDetail(Map.of("count", count, "first_id", ids.getFirst(), "last_id", ids.getLast())));
        return new LicenseBatch(issued);
    }

    private Inserted insertUnique(String remark, int adminId) {
        for (int attempt = 0; ; attempt++) {
            ActivationCode code = ActivationCode.generate();
            License license = License.issue(hasher.hash(code.value()), code.hint(), remark, adminId);
            try {
                mapper.insert(license);
                return new Inserted(code, license);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_COLLISION_RETRIES) {
                    throw e;
                }
            }
        }
    }

    private record Inserted(ActivationCode code, License license) {
    }

    private ApiErrorDetails expiredDetails(String snapshotJson) {
        JsonNode ids = objectMapper.readTree(snapshotJson).get("license_ids");
        List<Long> licenseIds = new ArrayList<>();
        if (ids != null) {
            ids.forEach(node -> licenseIds.add(node.asLong()));
        }
        return licenseIds.isEmpty() ? null : licenseIdsDetails(licenseIds);
    }

    private static ApiErrorDetails licenseIdsDetails(List<Long> ids) {
        return new ApiErrorDetails(null, null, null, null, null, ids, null);
    }

    static String requireReason(String reason) {
        String normalized = blankToNull(reason);
        if (normalized == null || normalized.length() > MAX_REASON) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/reason"));
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String likePattern(String keyword) {
        String normalized = blankToNull(keyword);
        if (normalized == null) {
            return null;
        }
        String escaped = normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
