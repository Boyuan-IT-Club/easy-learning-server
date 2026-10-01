package com.earlylearning.early_learning_server.identity.service.impl;

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
import com.earlylearning.early_learning_server.identity.service.AdminAccountService;
import com.earlylearning.early_learning_server.security.service.TokenService;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link AdminAccountService} 的实现。
 *
 * <p>幂等快照是不含密码的响应，指纹里是密码的 HMAC；改密码或停用的 Token 吊销在提交后执行。
 */
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final IdempotencyScope CREATE = IdempotencyScope.ADMIN_ACCOUNT_CREATE;

    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IdempotencyService idempotency;
    private final KeyedHasher hasher;
    private final ObjectMapper objectMapper;

    public AdminAccountServiceImpl(AdminAccountMapper mapper,
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

    @Override
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

    @Override
    @Transactional
    public AdminAccountResponse bootstrap(String rawUsername, String password) {
        return AdminAccountResponse.from(insert(Usernames.normalize(rawUsername, "admin.bootstrap.username"),
                AdminPasswordPolicy.require(password, "admin.bootstrap.password")));
    }

    @Override
    public boolean anyExists() {
        return mapper.countAll() > 0;
    }

    @Override
    public PageResponse<AdminAccountResponse> list(PageQuery page, String rawUsername, AdminStatus status) {
        String username = rawUsername == null ? null : Usernames.normalize(rawUsername, "/parameters/username");
        String statusValue = status == null ? null : status.name();
        return PageResponse.of(page, mapper.selectPage(username, statusValue, page.pageSize(), page.offset()),
                mapper.countMatching(username, statusValue), AdminAccountResponse::from);
    }

    @Override
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
