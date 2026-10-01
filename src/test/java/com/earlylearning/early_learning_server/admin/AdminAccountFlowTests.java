package com.earlylearning.early_learning_server.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.earlylearning.early_learning_server.admin.application.AdminAccountService;
import com.earlylearning.early_learning_server.admin.application.AdminLoginService;
import com.earlylearning.early_learning_server.admin.domain.AdminAccountSummary;
import com.earlylearning.early_learning_server.auth.application.TokenService;
import com.earlylearning.early_learning_server.support.AuthFixtures;
import com.earlylearning.early_learning_server.support.HttpApi;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理员控制（契约 adminLogin、createAdminAccount、listAdminAccounts、updateAdminAccount）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAccountFlowTests {

    @Autowired
    private WebServerApplicationContext context;
    @Autowired
    private AdminAccountService admins;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    private HttpApi api;
    private AuthFixtures fixtures;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        fixtures = new AuthFixtures(api, admins, tokenService, redis);
        fixtures.clearRateLimits();
    }

    // ---------- 登录 ----------

    @Test
    void loginReturnsASessionUsableOnAdminEndpoints() {
        AdminAccountSummary admin = fixtures.newAdmin();

        JsonNode session = login(admin.username().toUpperCase(), AuthFixtures.ADMIN_PASSWORD).expect(200, "OK")
                .conformsTo("adminLogin").data();

        assertThat(session.get("token_type").asString()).isEqualTo("Bearer");
        assertThat(session.get("token").asString()).startsWith("adt_");
        assertThat(session.get("account").get("id").asInt()).isEqualTo(admin.id());
        api.get("/admin/accounts").bearer(session.get("token").asString()).send().expect(200, "OK");
    }

    @Test
    void wrongPasswordAndUnknownUserLookTheSame() {
        AdminAccountSummary admin = fixtures.newAdmin();
        HttpApi.Response wrong = login(admin.username(), "Wrong-Password-1").expect(401, "INVALID_CREDENTIALS")
                .conformsTo("adminLogin");
        HttpApi.Response unknown = login(AuthFixtures.uniqueName("nobody_"), "Wrong-Password-1")
                .expect(401, "INVALID_CREDENTIALS");
        assertThat(unknown.body()).isEqualTo(wrong.body());
    }

    @Test
    void passwordIsNotTrimmed() {
        AdminAccountSummary admin = fixtures.newAdmin();
        login(admin.username(), " " + AuthFixtures.ADMIN_PASSWORD).expect(401, "INVALID_CREDENTIALS");
    }

    @Test
    void disabledAdminIsRejectedOnlyWithTheRightPassword() {
        AdminAccountSummary admin = fixtures.newAdmin();
        jdbc.update("UPDATE admin_account SET status = 'DISABLED' WHERE id = ?", admin.id());

        login(admin.username(), AuthFixtures.ADMIN_PASSWORD).expect(403, "ACCOUNT_DISABLED").conformsTo("adminLogin");
        login(admin.username(), "Wrong-Password-1").expect(401, "INVALID_CREDENTIALS");
    }

    @Test
    void repeatedFailuresLockTheUsername() {
        AdminAccountSummary admin = fixtures.newAdmin();
        int limit = AdminLoginService.LOGIN_FAILURES.limit();
        for (int i = 1; i < limit; i++) {
            login(admin.username(), "Wrong-Password-1").expect(401, "INVALID_CREDENTIALS");
        }
        login(admin.username(), "Wrong-Password-1").expect(429, "RATE_LIMITED").conformsTo("adminLogin");
        // 锁定期内正确密码也拒绝
        login(admin.username(), AuthFixtures.ADMIN_PASSWORD).expect(429, "RATE_LIMITED");
    }

    @Test
    void loginValidatesInput() {
        login("ab", AuthFixtures.ADMIN_PASSWORD).expect(400, "INVALID_REQUEST").conformsTo("adminLogin");
        login("admin_ok", "short").expect(400, "INVALID_REQUEST");
        login("admin_ok", "x".repeat(129)).expect(400, "INVALID_REQUEST");
        api.post("/admin/login", "{\"username\":\"admin_ok\",\"password\":\"Long-Enough-1\",\"otp\":\"1\"}").send()
                .expect(400, "INVALID_REQUEST");
    }

    // ---------- 创建与列表 ----------

    @Test
    void createNormalizesUsernameAndReplaysForTheSameKey() {
        String token = fixtures.adminToken();
        String username = AuthFixtures.uniqueName("New_Admin_");
        String key = HttpApi.newKey();

        HttpApi.Response created = create(token, username, "Created-Pass-01", key).expect(201, "OK")
                .conformsTo("createAdminAccount");
        JsonNode account = created.data();
        assertThat(account.get("username").asString()).isEqualTo(username.toLowerCase());
        assertThat(account.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(created.body()).doesNotContain("Created-Pass-01").doesNotContain("password");

        assertThat(create(token, username, "Created-Pass-01", key).expect(201, "OK").body()).isEqualTo(created.body());
        create(token, username, "Other-Pass-0001", key).expect(409, "IDEMPOTENCY_CONFLICT")
                .conformsTo("createAdminAccount");
        create(token, username.toUpperCase(), "Created-Pass-01", HttpApi.newKey()).expect(409, "USERNAME_EXISTS");

        // 新账号可以登录
        login(username, "Created-Pass-01").expect(200, "OK");
        // 幂等快照不含密码
        String snapshot = jdbc.queryForObject("SELECT response_body FROM idempotency_record WHERE scope = ? "
                + "ORDER BY id DESC LIMIT 1", String.class, "admin.account.create");
        assertThat(snapshot).doesNotContain("Created-Pass-01");
    }

    @Test
    void createRequiresIdempotencyKeyAndValidBody() {
        String token = fixtures.adminToken();
        api.post("/admin/accounts", "{\"username\":\"admin_nokey\",\"password\":\"Long-Enough-1\"}").bearer(token)
                .send().expect(400, "INVALID_REQUEST").conformsTo("createAdminAccount");
        create(token, "a b", "Long-Enough-1", HttpApi.newKey()).expect(400, "INVALID_REQUEST");
        create(token, "admin_short_pw", "1234567", HttpApi.newKey()).expect(400, "INVALID_REQUEST");
    }

    @Test
    void listFiltersByExactUsernameAndStatus() {
        String token = fixtures.adminToken();
        AdminAccountSummary target = fixtures.newAdmin();

        JsonNode exact = api.get("/admin/accounts?username=" + target.username().toUpperCase()).bearer(token)
                .send().expect(200, "OK").conformsTo("listAdminAccounts").data();
        assertThat(exact.get("total").asLong()).isEqualTo(1);
        assertThat(exact.get("items").get(0).get("id").asInt()).isEqualTo(target.id());

        // 精确匹配，不是包含匹配
        String prefix = target.username().substring(0, target.username().length() - 1);
        assertThat(api.get("/admin/accounts?username=" + prefix).bearer(token).send().expect(200, "OK")
                .data().get("total").asLong()).isZero();

        api.get("/admin/accounts?status=active").bearer(token).send().expect(400, "INVALID_REQUEST")
                .conformsTo("listAdminAccounts");
    }

    // ---------- 修改 ----------

    @Test
    void changingPasswordRevokesAllTokensOfThatAdmin() {
        String operator = fixtures.adminToken();
        AdminAccountSummary target = fixtures.newAdmin();
        String targetToken = login(target.username(), AuthFixtures.ADMIN_PASSWORD).expect(200, "OK")
                .data().get("token").asString();

        JsonNode updated = patch(operator, target.id(), "{\"password\":\"Changed-Pass-01\"}").expect(200, "OK")
                .conformsTo("updateAdminAccount").data();
        assertThat(updated.get("status").asString()).isEqualTo("ACTIVE");

        api.get("/admin/accounts").bearer(targetToken).send().expect(401, "TOKEN_INVALID");
        login(target.username(), AuthFixtures.ADMIN_PASSWORD).expect(401, "INVALID_CREDENTIALS");
        login(target.username(), "Changed-Pass-01").expect(200, "OK");
    }

    @Test
    void disablingRevokesTokensAndReenablingRequiresANewLogin() {
        String operator = fixtures.adminToken();
        AdminAccountSummary target = fixtures.newAdmin();
        String targetToken = fixtures.adminToken(target);

        patch(operator, target.id(), "{\"status\":\"DISABLED\"}").expect(200, "OK");
        api.get("/admin/accounts").bearer(targetToken).send().expect(401, "TOKEN_INVALID");
        // 重复提交相同状态
        patch(operator, target.id(), "{\"status\":\"DISABLED\"}").expect(200, "OK");

        patch(operator, target.id(), "{\"status\":\"ACTIVE\"}").expect(200, "OK");
        api.get("/admin/accounts").bearer(targetToken).send().expect(401, "TOKEN_INVALID");
        login(target.username(), AuthFixtures.ADMIN_PASSWORD).expect(200, "OK");
    }

    @Test
    void disabledAdminTokenIsRejectedOnTheNextRequest() {
        AdminAccountSummary target = fixtures.newAdmin();
        String token = fixtures.adminToken(target);
        // 绕过接口直接改库：每个请求都要查账号状态，不能只靠吊销
        jdbc.update("UPDATE admin_account SET status = 'DISABLED' WHERE id = ?", target.id());
        api.get("/admin/accounts").bearer(token).send().expect(403, "ACCOUNT_DISABLED");
    }

    @Test
    void updateValidatesInput() {
        String operator = fixtures.adminToken();
        AdminAccountSummary target = fixtures.newAdmin();
        patch(operator, target.id(), "{}").expect(400, "INVALID_REQUEST").conformsTo("updateAdminAccount");
        patch(operator, target.id(), "{\"status\":\"LOCKED\"}").expect(400, "INVALID_REQUEST");
        patch(operator, target.id(), "{\"password\":\"short\"}").expect(400, "INVALID_REQUEST");
        patch(operator, target.id(), "{\"username\":\"rename\"}").expect(400, "INVALID_REQUEST");
        patch(operator, 999999999, "{\"status\":\"ACTIVE\"}").expect(404, "RESOURCE_NOT_FOUND")
                .conformsTo("updateAdminAccount");
    }

    private HttpApi.Response login(String username, String password) {
        return api.post("/admin/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}").send();
    }

    private HttpApi.Response create(String token, String username, String password, String key) {
        return api.post("/admin/accounts", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .bearer(token).idempotencyKey(key).send();
    }

    private HttpApi.Response patch(String token, int id, String body) {
        return api.patch("/admin/accounts/" + id, body).bearer(token).send();
    }
}
