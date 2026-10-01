package com.earlylearning.early_learning_server.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.earlylearning.early_learning_server.security.service.TokenService;
import com.earlylearning.early_learning_server.identity.service.AdminAccountService;
import com.earlylearning.early_learning_server.identity.service.TeacherRegistrationService;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.support.AuthFixtures;
import com.earlylearning.early_learning_server.support.HttpApi;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 教师注册与刷新（契约 registerTeacher、refreshTeacherToken），走真实 HTTP 与真实库、Redis。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTests {

    private static final String SCORE_TASK = "/api/ai/tasks/33333333-3333-3333-3333-333333333333";

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
    private String adminToken;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        fixtures = new AuthFixtures(api, admins, tokenService, redis);
        fixtures.clearRateLimits();
        adminToken = fixtures.adminToken();
    }

    // ---------- 注册 ----------

    @Test
    void registerCreatesAccountClaimsCodeAndReturnsTokenPair() {
        AuthFixtures.IssuedCode code = fixtures.issueLicense(adminToken);
        String username = AuthFixtures.uniqueName("Teacher_");

        JsonNode data = fixtures.register(code.code(), username).expect(201, "OK")
                .conformsTo("registerTeacher").data();

        assertThat(data.get("token_type").asString()).isEqualTo("Bearer");
        assertThat(data.get("access_token").asString()).startsWith("at_");
        assertThat(data.get("refresh_token").asString()).startsWith("rt_");
        assertThat(data.get("user").get("username").asString()).isEqualTo(username.toLowerCase());
        assertThat(data.get("user").get("status").asInt()).isEqualTo(1);

        int userId = data.get("user").get("id").asInt();
        assertThat(jdbc.queryForMap("SELECT status, user_id, activated_at FROM user_license WHERE id = ?", code.id()))
                .containsEntry("status", "ACTIVE")
                .containsEntry("user_id", userId)
                .extractingByKey("activated_at").isNotNull();
        assertThat(jdbc.queryForObject("SELECT refresh_token_hash FROM user_account WHERE id = ?", String.class, userId))
                .isEqualTo(Tokens.sha256Hex(data.get("refresh_token").asString()));

        // 新 access 立即可用（走到业务，任务不存在 404）
        api.get(SCORE_TASK).bearer(data.get("access_token").asString()).send().expect(404, "TASK_NOT_FOUND");
    }

    @Test
    void registerReplaysTheSameResultForTheSameKey() {
        AuthFixtures.IssuedCode code = fixtures.issueLicense(adminToken);
        String username = AuthFixtures.uniqueName("t_");
        String key = HttpApi.newKey();

        HttpApi.Response first = fixtures.register(code.code(), username, key).expect(201, "OK");
        HttpApi.Response again = fixtures.register(code.code(), username, key).expect(201, "OK");

        assertThat(again.body()).isEqualTo(first.body());
        fixtures.register(code.code(), AuthFixtures.uniqueName("t_"), key).expect(409, "IDEMPOTENCY_CONFLICT")
                .conformsTo("registerTeacher");
    }

    @Test
    void registerReplayAfterCacheLossDoesNotConsumeAnotherCode() {
        AuthFixtures.IssuedCode code = fixtures.issueLicense(adminToken);
        String username = AuthFixtures.uniqueName("t_");
        String key = HttpApi.newKey();
        fixtures.register(code.code(), username, key).expect(201, "OK");

        deleteKeys("el:idem:sensitive:auth.register:*");

        fixtures.register(code.code(), username, key).expect(409, "SENSITIVE_RESULT_EXPIRED")
                .conformsTo("registerTeacher");
    }

    @Test
    void registerRejectsUsedRevokedAndUnknownCodes() {
        AuthFixtures.IssuedCode used = fixtures.issueLicense(adminToken);
        fixtures.register(used.code(), AuthFixtures.uniqueName("t_")).expect(201, "OK");
        fixtures.register(used.code(), AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_UNAVAILABLE")
                .conformsTo("registerTeacher");

        AuthFixtures.IssuedCode revoked = fixtures.issueLicense(adminToken);
        api.post("/admin/licenses/" + revoked.id() + "/revoke", null).bearer(adminToken).send().expect(200, "OK");
        fixtures.register(revoked.code(), AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_REVOKED")
                .conformsTo("registerTeacher");

        fixtures.register("ZZZZZZZZZZZZZZZZ", AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_UNAVAILABLE");
        // 原样核对，不做规范化：小写的码不匹配（客户端负责规范化）
        AuthFixtures.IssuedCode fresh = fixtures.issueLicense(adminToken);
        fixtures.register(fresh.code().toLowerCase(), AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_UNAVAILABLE");
    }

    @Test
    void registerRejectsUsernamesThatDifferOnlyInCase() {
        String username = AuthFixtures.uniqueName("dup_");
        fixtures.register(fixtures.issueLicense(adminToken).code(), username).expect(201, "OK");

        AuthFixtures.IssuedCode second = fixtures.issueLicense(adminToken);
        fixtures.register(second.code(), username.toUpperCase()).expect(409, "USERNAME_EXISTS")
                .conformsTo("registerTeacher");
        // 失败整体回滚：激活码仍可用
        assertThat(jdbc.queryForObject("SELECT status FROM user_license WHERE id = ?", String.class, second.id()))
                .isEqualTo("UNUSED");
    }

    @Test
    void registerValidatesInput() {
        String code = fixtures.issueLicense(adminToken).code();
        fixtures.register(code, "ab").expect(400, "INVALID_REQUEST").conformsTo("registerTeacher");
        fixtures.register(code, "has space").expect(400, "INVALID_REQUEST");
        fixtures.register(code, "a".repeat(65)).expect(400, "INVALID_REQUEST");

        // 契约：禁止提交 password / password_hash；多带任何字段都 400
        api.post("/api/auth/register", "{\"activation_code\":\"" + code + "\",\"username\":\"t_pw_field\","
                        + "\"password\":\"secret-123\"}")
                .idempotencyKey(HttpApi.newKey()).send().expect(400, "INVALID_REQUEST");
        // 缺幂等键
        api.post("/api/auth/register", "{\"activation_code\":\"" + code + "\",\"username\":\"t_no_key\"}")
                .send().expect(400, "INVALID_REQUEST");
        api.post("/api/auth/register", "{\"activation_code\":\"" + code + "\",\"username\":\"t_bad_key\"}")
                .idempotencyKey("not-a-uuid").send().expect(400, "INVALID_REQUEST");
    }

    // ---------- 刷新 ----------

    @Test
    void refreshRotatesBothTokens() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);

        JsonNode data = fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK")
                .conformsTo("refreshTeacherToken").data();

        String newRefresh = data.get("refresh_token").asString();
        assertThat(newRefresh).isNotEqualTo(teacher.refreshToken());
        assertThat(data.get("access_token").asString()).isNotEqualTo(teacher.accessToken());
        assertThat(currentRefreshHash(teacher.userId())).isEqualTo(Tokens.sha256Hex(newRefresh));
        api.get(SCORE_TASK).bearer(data.get("access_token").asString()).send().expect(404, "TASK_NOT_FOUND");
    }

    @Test
    void previousTokenWithinGraceReturnsTheSamePairEvenWithANewKey() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        HttpApi.Response first = fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK");
        String hashAfterFirst = currentRefreshHash(teacher.userId());

        HttpApi.Response retry = fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK")
                .conformsTo("refreshTeacherToken");

        assertThat(retry.data()).isEqualTo(first.data());
        assertThat(currentRefreshHash(teacher.userId())).as("宽限内不再次轮换").isEqualTo(hashAfterFirst);
    }

    @Test
    void sameKeySameTokenReplaysAndDifferentTokenConflicts() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        String key = HttpApi.newKey();
        HttpApi.Response first = fixtures.refresh(teacher.refreshToken(), key).expect(200, "OK");

        assertThat(fixtures.refresh(teacher.refreshToken(), key).expect(200, "OK").body()).isEqualTo(first.body());
        fixtures.refresh(first.data().get("refresh_token").asString(), key).expect(409, "IDEMPOTENCY_CONFLICT")
                .conformsTo("refreshTeacherToken");
    }

    @Test
    void olderTokensDieOnceTheNewOneRotatesAgain() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        String second = fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK")
                .data().get("refresh_token").asString();
        String third = fixtures.refresh(second, HttpApi.newKey()).expect(200, "OK")
                .data().get("refresh_token").asString();

        // 最早的一枚：宽限记录仍在，但它指向的 second 已不是数据库当前值
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(401, "REFRESH_TOKEN_INVALID")
                .conformsTo("refreshTeacherToken");
        // 紧邻当前的一枚仍在宽限内
        assertThat(fixtures.refresh(second, HttpApi.newKey()).expect(200, "OK").data().get("refresh_token").asString())
                .isEqualTo(third);
    }

    @Test
    void previousTokenAfterGraceIsInvalid() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK");

        // 模拟 30 秒到期 / 进程重启丢失
        redis.delete("el:auth:rt-grace:" + Tokens.sha256Hex(teacher.refreshToken()));

        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void sameKeyReplayAfterCacheLossIsSensitiveResultExpired() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        String key = HttpApi.newKey();
        fixtures.refresh(teacher.refreshToken(), key).expect(200, "OK");

        deleteKeys("el:idem:sensitive:auth.refresh:*");

        fixtures.refresh(teacher.refreshToken(), key).expect(409, "SENSITIVE_RESULT_EXPIRED")
                .conformsTo("refreshTeacherToken");
    }

    @Test
    void disabledTeacherCannotRefreshOrCallApiButReenablingRestoresBoth() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        setTeacherStatus(teacher.userId(), 0);

        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(403, "ACCOUNT_DISABLED");
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(403, "ACCOUNT_DISABLED")
                .conformsTo("refreshTeacherToken");

        // 契约：停用不吊销凭证；重新启用后未过期的 access 与未轮换的 refresh 恢复可用
        setTeacherStatus(teacher.userId(), 1);
        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(404, "TASK_NOT_FOUND");
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK");
    }

    @Test
    void graceRetryStillChecksAccountStatus() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(200, "OK");
        setTeacherStatus(teacher.userId(), 0);

        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(403, "ACCOUNT_DISABLED");
    }

    @Test
    void revokingTheLicenseBlocksAccessAndRefresh() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        api.post("/admin/licenses/" + teacher.licenseId() + "/revoke", null).bearer(adminToken).send()
                .expect(200, "OK");

        // 撤销同时禁用了教师，所以先报账号停用
        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(403, "ACCOUNT_DISABLED");
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(403, "ACCOUNT_DISABLED");

        // 即使绕过管理接口把账号改回启用，激活码已撤销仍然拦截
        setTeacherStatus(teacher.userId(), 1);
        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(403, "LICENSE_REVOKED");
        fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()).expect(403, "LICENSE_REVOKED");
    }

    @Test
    void refreshValidatesInput() {
        api.post("/api/auth/refresh", "{\"refresh_token\":\"\"}").idempotencyKey(HttpApi.newKey()).send()
                .expect(400, "INVALID_REQUEST").conformsTo("refreshTeacherToken");
        api.post("/api/auth/refresh", "{\"refresh_token\":\"rt_x\",\"extra\":1}").idempotencyKey(HttpApi.newKey())
                .send().expect(400, "INVALID_REQUEST");
        api.post("/api/auth/refresh", "{\"refresh_token\":\"rt_x\"}").send().expect(400, "INVALID_REQUEST");
    }

    @Test
    void registerIsRateLimitedPerIp() {
        int limit = TeacherRegistrationService.PER_IP.limit();
        for (int i = 0; i < limit; i++) {
            fixtures.register("ZZZZZZZZZZZZZZZZ", AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_UNAVAILABLE");
        }
        fixtures.register("ZZZZZZZZZZZZZZZZ", AuthFixtures.uniqueName("t_")).expect(429, "RATE_LIMITED")
                .conformsTo("registerTeacher");
    }

    // ---------- 并发 ----------

    @Test
    void concurrentRefreshesOfTheSameTokenGetTheSamePair() throws Exception {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);

        List<HttpApi.Response> responses = concurrently(4,
                () -> fixtures.refresh(teacher.refreshToken(), HttpApi.newKey()));

        // 一个轮换，其余在行锁上等到它提交后走宽限，拿到同一组
        JsonNode first = responses.getFirst().expect(200, "OK").data();
        for (HttpApi.Response response : responses) {
            assertThat(response.expect(200, "OK").data()).isEqualTo(first);
        }
        assertThat(currentRefreshHash(teacher.userId()))
                .isEqualTo(Tokens.sha256Hex(first.get("refresh_token").asString()));
    }

    @Test
    void concurrentRegistrationsClaimACodeOnlyOnce() throws Exception {
        AuthFixtures.IssuedCode code = fixtures.issueLicense(adminToken);

        List<HttpApi.Response> responses = concurrently(4,
                () -> fixtures.register(code.code(), AuthFixtures.uniqueName("t_")));

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409)
                .allSatisfy(r -> assertThat(r.code()).isEqualTo("LICENSE_UNAVAILABLE")).hasSize(3);
    }

    private static List<HttpApi.Response> concurrently(int n, Callable<HttpApi.Response> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<HttpApi.Response>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<HttpApi.Response> responses = new ArrayList<>();
            for (Future<HttpApi.Response> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String currentRefreshHash(int userId) {
        return jdbc.queryForObject("SELECT refresh_token_hash FROM user_account WHERE id = ?", String.class, userId);
    }

    private void setTeacherStatus(int userId, int status) {
        jdbc.update("UPDATE user_account SET status = ? WHERE id = ?", status, userId);
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
