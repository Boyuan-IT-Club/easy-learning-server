package com.earlylearning.early_learning_server.support;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.admin.infrastructure.AdminAccountMapper;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.auth.domain.IssuedToken;

/**
 * 测试用账号与凭证。
 *
 * <p>其他模块的接口测试不必走一遍注册流程：直接建一个管理员并通过 {@link TokenService} 签发 Token。
 * 用户名带随机后缀，测试之间互不干扰，也不依赖回滚。
 */
@Component
public class TestAccounts {

    public static final String ADMIN_PASSWORD = "Test-Admin-Password-1";

    private final AdminAccountMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final StringRedisTemplate redis;

    public TestAccounts(AdminAccountMapper adminMapper,
                        PasswordEncoder passwordEncoder,
                        TokenService tokenService,
                        StringRedisTemplate redis) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.redis = redis;
    }

    /** @return 新管理员的用户名；密码为 {@link #ADMIN_PASSWORD} */
    public AdminAccount createAdmin() {
        AdminAccount admin = AdminAccount.create("adm_" + UUID.randomUUID().toString().substring(0, 8),
                passwordEncoder.encode(ADMIN_PASSWORD));
        adminMapper.insert(admin);
        return admin;
    }

    /** 建一个管理员并直接签发 Token（不经过登录接口）。 */
    public String adminToken() {
        IssuedToken token = tokenService.issueAdmin(createAdmin().getId());
        return token.value();
    }

    /** 清掉限流与锁定计数：所有测试都从 127.0.0.1 发请求，不清会互相撞上限流。 */
    public void resetRateLimits() {
        Set<String> keys = redis.keys("el:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public static String newDeviceId() {
        return UUID.randomUUID().toString();
    }
}
