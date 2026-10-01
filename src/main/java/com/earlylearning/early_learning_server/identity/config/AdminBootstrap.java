package com.earlylearning.early_learning_server.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.identity.config.AdminBootstrapProperties;
import com.earlylearning.early_learning_server.identity.dto.AdminAccountResponse;
import com.earlylearning.early_learning_server.identity.service.AdminAccountService;

/** 启动时按 {@link AdminBootstrapProperties} 创建首个管理员。 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminAccountService adminAccountService;
    private final AdminBootstrapProperties adminBootstrapProperties;

    public AdminBootstrap(AdminAccountService adminAccountService, AdminBootstrapProperties adminBootstrapProperties) {
        this.adminAccountService = adminAccountService;
        this.adminBootstrapProperties = adminBootstrapProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminAccountService.anyExists()) {
            return;
        }
        if (!adminBootstrapProperties.configured()) {
            log.warn("尚无管理员，且未配置 ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD，管理端暂无法登录");
            return;
        }
        try {
            AdminAccountResponse created = adminAccountService.bootstrap(adminBootstrapProperties.username(),
                    adminBootstrapProperties.password());
            log.info("已初始化首个管理员 id={} username={}", created.id(), created.username());
        } catch (BusinessException e) {
            // 多实例同时启动时，另一个实例可能已经建好；配置不合法时也只告警，不阻止启动
            log.warn("首个管理员初始化未完成 code={}", e.getErrorCode().name());
        }
    }
}
