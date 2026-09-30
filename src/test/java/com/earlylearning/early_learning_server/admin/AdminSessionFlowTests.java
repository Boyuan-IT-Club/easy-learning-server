package com.earlylearning.early_learning_server.admin;

import static com.earlylearning.early_learning_server.support.HttpApi.headers;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.earlylearning.early_learning_server.admin.domain.AdminAccount;
import com.earlylearning.early_learning_server.support.HttpApi;
import com.earlylearning.early_learning_server.support.HttpApi.Response;
import com.earlylearning.early_learning_server.support.TestAccounts;

/** 管理员登录、会话与维护的端到端行为。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminSessionFlowTests {

    @Autowired
    private WebServerApplicationContext context;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpApi api;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        accounts.resetRateLimits();
    }

    @Test
    void 登录拿到Token并能访问me() {
        AdminAccount admin = accounts.createAdmin();
        Response login = login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        String token = login.dataText("admin_token");
        assertThat(token).startsWith("adt_");

        Response me = api.get("/admin/auth/me", bearer(token));
        assertThat(me.dataText("username")).isEqualTo(admin.getUsername());
    }

    @Test
    void 用户名不存在与密码错误回同一个错误码() {
        AdminAccount admin = accounts.createAdmin();
        assertThat(login(admin.getUsername(), "wrong-password").code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(login("nobody_" + UUID.randomUUID().toString().substring(0, 6), "whatever").code())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void A24_连续失败5次后锁定_输对也拒绝_并留有审计() {
        AdminAccount admin = accounts.createAdmin();
        for (int i = 0; i < 5; i++) {
            assertThat(login(admin.getUsername(), "wrong-password").code()).isEqualTo("INVALID_CREDENTIALS");
        }
        Response locked = login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD);
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("RATE_LIMITED");

        Long failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_LOGIN_FAILED' AND target_id = ?",
                Long.class, admin.getId().toString());
        assertThat(failures).isGreaterThanOrEqualTo(5L);
    }

    @Test
    void 退出后Token失效() {
        String token = login(accounts.createAdmin().getUsername(), TestAccounts.ADMIN_PASSWORD).dataText("admin_token");
        assertThat(api.post("/admin/auth/logout", Map.of(), bearer(token)).status()).isEqualTo(200);
        assertThat(api.get("/admin/auth/me", bearer(token)).code()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void 改密码后其他会话失效_当前会话保留() {
        AdminAccount admin = accounts.createAdmin();
        String current = login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD).dataText("admin_token");
        String other = login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD).dataText("admin_token");

        Response changed = api.post("/admin/auth/password",
                Map.of("old_password", TestAccounts.ADMIN_PASSWORD, "new_password", "Brand-New-Password-2"),
                bearer(current));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(api.get("/admin/auth/me", bearer(current)).status()).isEqualTo(200);
        assertThat(api.get("/admin/auth/me", bearer(other)).code()).isEqualTo("TOKEN_INVALID");
        assertThat(login(admin.getUsername(), "Brand-New-Password-2").status()).isEqualTo(200);
    }

    @Test
    void 不能停用自己() {
        AdminAccount admin = accounts.createAdmin();
        String token = login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD).dataText("admin_token");
        Response response = api.post("/admin/admins/" + admin.getId() + "/disable", Map.of(), bearer(token));
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.code()).isEqualTo("ADMIN_LAST_ACTIVE");
    }

    @Test
    void 被停用的管理员Token立即失效() {
        String actorToken = accounts.adminToken();
        AdminAccount target = accounts.createAdmin();
        String targetToken = login(target.getUsername(), TestAccounts.ADMIN_PASSWORD).dataText("admin_token");

        api.post("/admin/admins/" + target.getId() + "/disable", Map.of(), bearer(actorToken));
        assertThat(api.get("/admin/auth/me", bearer(targetToken)).status()).isEqualTo(401);
        assertThat(login(target.getUsername(), TestAccounts.ADMIN_PASSWORD).code()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void 审计可按目标查询() {
        AdminAccount admin = accounts.createAdmin();
        login(admin.getUsername(), TestAccounts.ADMIN_PASSWORD);
        Response logs = api.get("/admin/audit-logs?action=ADMIN_LOGIN&target_type=ADMIN&target_id=" + admin.getId(),
                bearer(accounts.adminToken()));
        assertThat(logs.status()).isEqualTo(200);
        assertThat(logs.data().get("total").asLong()).isEqualTo(1L);
    }

    private Response login(String username, String password) {
        return api.post("/admin/auth/login", Map.of("username", username, "password", password), Map.of());
    }

    private static Map<String, String> bearer(String token) {
        return headers("Authorization", "Bearer " + token);
    }
}
