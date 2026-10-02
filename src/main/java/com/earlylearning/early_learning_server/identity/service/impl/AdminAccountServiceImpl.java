package com.earlylearning.early_learning_server.identity.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.enums.AdminStatus;
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
import com.earlylearning.early_learning_server.identity.dto.AdminAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.AdminAccountMapper;
import com.earlylearning.early_learning_server.identity.model.AdminPasswordPolicy;
import com.earlylearning.early_learning_server.identity.service.AdminAccountService;
import com.earlylearning.early_learning_server.security.service.TokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AdminAccountService} 的实现。
 *
 * <p>幂等快照是不含密码的响应，指纹里是密码的 HMAC；改密码或停用的 Token 吊销在提交后执行。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final IdempotencyScope CREATE = IdempotencyScope.ADMIN_ACCOUNT_CREATE;

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IdempotencyService idempotencyService;
    private final KeyedHasher keyedHasher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AdminAccountResponse create(String rawUsername, String password, String idempotencyKey) {
        String username = Usernames.normalize(rawUsername, "/username");
        AdminPasswordPolicy.require(password, "/password");
        String fingerprint = InputFingerprint.of(username, keyedHasher.hash(password));
        Optional<StoredResponse> replayed = idempotencyService.claim(CREATE, idempotencyKey, fingerprint);
        if (replayed.isPresent()) {
            log.info("创建管理员请求重放，返回首次结果 username={}", username);
            return objectMapper.readValue(replayed.get().body(), AdminAccountResponse.class);
        }
        AdminAccountResponse created = AdminAccountResponse.from(insert(username, password));
        idempotencyService.record(CREATE, idempotencyKey, 201, created);
        log.info("创建管理员 adminId={} username={}", created.id(), created.username());
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
        return adminAccountMapper.countAll() > 0;
    }

    @Override
    public PageResponse<AdminAccountResponse> list(PageQuery page, String rawUsername, AdminStatus status) {
        String username = rawUsername == null ? null : Usernames.normalize(rawUsername, "/parameters/username");
        String statusValue = status == null ? null : status.name();
        List<AdminAccount> rows = adminAccountMapper.selectPage(username, statusValue, page.pageSize(), page.offset());
        return PageResponse.of(page, rows, adminAccountMapper.countMatching(username, statusValue),
                AdminAccountResponse::from);
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

        AdminAccount account = adminAccountMapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean revokeTokens = false;
        if (password != null) {
            adminAccountMapper.updatePassword(id, passwordEncoder.encode(password));
            revokeTokens = true;
        }
        if (target != null && target != account.getStatus()) {
            adminAccountMapper.updateStatus(id, target.name());
            revokeTokens |= target == AdminStatus.DISABLED;
        }
        if (revokeTokens) {
            tokenService.revokeAdminAfterCommit(id);
        }
        log.info("更新管理员 adminId={} passwordChanged={} status={} tokensRevoked={}",
                id, password != null, target == null ? account.getStatus() : target, revokeTokens);
        // 回读：拿到 ON UPDATE 刷新后的 updated_at
        return AdminAccountResponse.from(adminAccountMapper.selectById(id));
    }

    private AdminAccount insert(String username, String password) {
        if (adminAccountMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AdminStatus.ACTIVE);
        try {
            adminAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        return adminAccountMapper.selectById(account.getId());
    }
}
