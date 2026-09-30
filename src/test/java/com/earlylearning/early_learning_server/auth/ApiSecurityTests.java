package com.earlylearning.early_learning_server.auth;

import static com.earlylearning.early_learning_server.support.HttpApi.headers;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import com.earlylearning.early_learning_server.support.HttpApi;
import com.earlylearning.early_learning_server.support.TestAccounts;

/**
 * 安全链的对外行为 —— 走真实 HTTP，不是 MockMvc standalone（那套不带安全过滤器，测不出这里的问题）。
 *
 * <p>守的坑：Spring Security 默认开启 CSRF，会在认证之前拒掉不安全方法，于是 POST 一律 401 而 GET 正常；
 * 调用方是非浏览器客户端，没有取 CSRF 令牌这一步。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSecurityTests {

    @Autowired
    private WebServerApplicationContext context;

    @Autowired
    private TestAccounts accounts;

    private HttpApi api;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        accounts.resetRateLimits();
    }

    @Test
    void 教师接口不带Token返回401_TOKEN_MISSING() {
        HttpApi.Response response = api.get("/api/ai/tasks/33333333-3333-3333-3333-333333333333", Map.of());
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("TOKEN_MISSING");
    }

    @Test
    void 管理接口不带Token返回401() {
        assertThat(api.get("/admin/files", Map.of()).status()).isEqualTo(401);
    }

    @Test
    void 伪造的Token返回401_TOKEN_INVALID() {
        HttpApi.Response response = api.get("/admin/files", headers("Authorization", "Bearer adt_forged"));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void 无人认领的前缀返回401_TOKEN_INVALID() {
        HttpApi.Response response = api.get("/admin/files", headers("Authorization", "Bearer rt_refresh-used-as-access"));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void 管理员Token可以访问管理接口_且POST不被CSRF拦下() {
        String token = accounts.adminToken();
        assertThat(api.get("/admin/files", headers("Authorization", "Bearer " + token)).status()).isEqualTo(200);
        // POST 能走到控制器（这里因缺少 Idempotency-Key 回 400），而不是被 CSRF 挡成 401/403
        HttpApi.Response post = api.post("/admin/licenses/batch", Map.of("count", 1),
                headers("Authorization", "Bearer " + token));
        assertThat(post.status()).isEqualTo(400);
    }

    @Test
    void 管理员Token访问教师接口返回403_AUTH_ROLE_MISMATCH() {
        HttpApi.Response response = api.get("/api/auth/me",
                headers("Authorization", "Bearer " + accounts.adminToken()));
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("AUTH_ROLE_MISMATCH");
    }

    @Test
    void 评分目录允许管理员访问() {
        HttpApi.Response response = api.get("/api/ai/rubrics",
                headers("Authorization", "Bearer " + accounts.adminToken()));
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    void 免登录接口即使带着过期或伪造的Bearer也不被拦() {
        // 平板在 access 过期后调刷新接口时可能还带着旧 Bearer，不能因此把刷新本身拒掉
        HttpApi.Response response = api.post("/api/auth/refresh", Map.of("refresh_token", "rt_unknown"),
                headers("Authorization", "Bearer at_expired", "X-Device-Id", TestAccounts.newDeviceId()));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void 两条链之外的路径一律拒绝() {
        assertThat(api.get("/something-else", Map.of()).status()).isEqualTo(401);
    }
}
