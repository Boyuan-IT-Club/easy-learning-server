package com.earlylearning.early_learning_server.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.admin.infrastructure.AdminAccountMapper;
import com.earlylearning.early_learning_server.audit.application.AuditLogService;
import com.earlylearning.early_learning_server.audit.domain.AuditAction;
import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.TargetType;

/**
 * 启动时创建初始管理员：admin_account 为空、且配置了用户名与密码时执行一次。
 *
 * <p>日志只记用户名，不记密码。多实例同时启动时，唯一键保证只建成一个。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminBootstrapProperties properties;
    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService audit;

    public AdminBootstrap(AdminBootstrapProperties properties,
                          AdminAccountMapper mapper,
                          PasswordEncoder passwordEncoder,
                          AuditLogService audit) {
        this.properties = properties;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.countAll() > 0) {
            return;
        }
        String username = properties.username();
        String password = properties.password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("尚无管理员：设置 ADMIN_BOOTSTRAP_USERNAME 与 ADMIN_BOOTSTRAP_PASSWORD 后重启以创建初始管理员");
            return;
        }
        AdminAccount.checkUsername(username, "/admin/bootstrap/username");
        AdminAccount.checkPasswordPolicy(password, "/admin/bootstrap/password");
        AdminAccount account = AdminAccount.create(username, passwordEncoder.encode(password));
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            log.info("初始管理员已由其他实例创建 username={}", username);
            return;
        }
        audit.record(AuditEntry.bySystem(AuditAction.ADMIN_BOOTSTRAPPED, TargetType.ADMIN, account.getId()));
        log.info("已创建初始管理员 username={}；请从环境变量中删除 ADMIN_BOOTSTRAP_PASSWORD", username);
    }
}
