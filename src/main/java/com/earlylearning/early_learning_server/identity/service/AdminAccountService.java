package com.earlylearning.early_learning_server.identity.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.identity.Usernames;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.AdminAccount;
import com.earlylearning.early_learning_server.entity.AdminStatus;
import com.earlylearning.early_learning_server.identity.dto.AdminAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.AdminAccountMapper;
import com.earlylearning.early_learning_server.identity.model.AdminPasswordPolicy;
import com.earlylearning.early_learning_server.security.service.TokenService;

import tools.jackson.databind.ObjectMapper;

/**
 * 管理员账号维护（契约 createAdminAccount、listAdminAccounts、updateAdminAccount）。所有管理员同权限，无 RBAC。
 * 改密码或停用后，提交后删除该管理员已签发的全部 Token。
 */
@Service
public class AdminAccountService {

    private static final IdempotencyScope CREATE = IdempotencyScope.ADMIN_ACCOUNT_CREATE;

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IdempotencyService idempotency;
    private final KeyedHasher hasher;
    private final ObjectMapper objectMapper;

    public AdminAccountService(AdminAccountMapper mapper,
                               PasswordEncoder passwordEncoder,
                               TokenService tokenService,
                               IdempotencyService idempotency,
                               KeyedHasher hasher,
                               ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.idempotency = idempotency;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
    }

    /** 仅已有管理员可创建；默认 ACTIVE。幂等快照是不含密码的响应（指纹里是密码的 HMAC）。 */
    @Transactional
    public AdminAccountResponse create(String rawUsername, String password, String idempotencyKey) {
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswordPolicy.require(password, "/password");
        String fingerprint = InputFingerprint.of(username, hasher.hash(password));
        Optional<StoredResponse> replayed = idempotency.claim(CREATE, idempotencyKey, fingerprint);
        if (replayed.isPresent()) {
            return objectMapper.readValue(replayed.get().body(), AdminAccountResponse.class);
        }
        AdminAccountResponse created = AdminAccountResponse.from(insert(username, password));
        idempotency.record(CREATE, idempotencyKey, 201, created);
        return created;
    }

    /** 部署初始化首个管理员时用；不走幂等。 */
    @Transactional
    public AdminAccountResponse bootstrap(String rawUsername, String password) {
        return AdminAccountResponse.from(insert(Usernames.normalize(rawUsername, "admin.bootstrap.username"),
                AdminPasswordPolicy.require(password, "admin.bootstrap.password")));
    }

    public boolean anyExists() {
        return mapper.countAll() > 0;
    }

    /** @param rawUsername 转小写后精确匹配 */
    public PageResponse<AdminAccountResponse> list(PageQuery page, String rawUsername, AdminStatus status) {
        String username = rawUsername == null ? null : Usernames.normalize(rawUsername, "/parameters/username");
        String statusValue = status == null ? null : status.name();
        return PageResponse.of(page, mapper.selectPage(username, statusValue, page.pageSize(), page.offset()),
                mapper.countMatching(username, statusValue), AdminAccountResponse::from);
    }

    /** 至少一项；未传字段保持原值。重复提交相同目标状态不产生额外变化。 */
    @Transactional
    public AdminAccountResponse update(int id, String password, String rawStatus) {
        if (password == null && rawStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (password != null) {
            AdminPasswordPolicy.require(password, "/password");
        }
        AdminStatus target = rawStatus == null ? null : AdminStatus.parse(rawStatus, "/status");

        AdminAccount account = mapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean revokeTokens = false;
        if (password != null) {
            mapper.updatePassword(id, passwordEncoder.encode(password));
            revokeTokens = true;
        }
        if (target != null && target != account.getStatus()) {
            mapper.updateStatus(id, target.name());
            revokeTokens |= target == AdminStatus.DISABLED;
        }
        if (revokeTokens) {
            tokenService.revokeAdminAfterCommit(id);
        }
        // 回读：拿到 ON UPDATE 刷新后的 updated_at
        return AdminAccountResponse.from(mapper.selectById(id));
    }

    /** 管理端每个请求都要确认账号仍是 ACTIVE（契约 AdminBearer），见 {@link AdminBearerAuthenticator}。 */
    Optional<AdminAccount> find(int id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    private AdminAccount insert(String username, String password) {
        if (mapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AdminStatus.ACTIVE);
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        return mapper.selectById(account.getId());
    }
}
