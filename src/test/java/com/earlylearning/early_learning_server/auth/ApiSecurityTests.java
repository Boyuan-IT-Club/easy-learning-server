package com.earlylearning.early_learning_server.auth;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.earlylearning.early_learning_server.admin.AdminAccountService;
import com.earlylearning.early_learning_server.common.secret.Tokens;
import com.earlylearning.early_learning_server.support.AuthFixtures;
import com.earlylearning.early_learning_server.support.HttpApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全链的对外行为 —— 走<b>真实 HTTP</b>，不是 standalone MockMvc（那套不带安全过滤器）。
 *
 * <p>角色规则逐条取自契约各操作的 {@code security}；另守一个踩过的坑：Spring Security 默认开启 CSRF，
 * 会在认证之前拒掉 POST，而契约的调用方是非浏览器客户端，没有取 CSRF 令牌这一步。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSecurityTests {

    private static final String VALID_SCORE_BODY = """
            {"request_id":"11111111-1111-1111-1111-111111111111",
             "input_revision":"22222222-2222-2222-2222-222222222222",
             "business_type":"ASSESSMENT","activity_id":"ACT_SEC","confirmed_text":"它是一只小狗。",
             "text_confirmed":true,"story_context":"故事依据",
             "question":{"question_id":"Q_1","text":"问题","hint":""},"attempt":"BEFORE_HINT","images":[]}""";

    @Autowired
    private WebServerApplicationContext context;
    @Autowired
    private AdminAccountService admins;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private RedisTokenStore store;
    @Autowired
    private StringRedisTemplate redis;

    private HttpApi api;
    private AuthFixtures fixtures;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        fixtures = new AuthFixtures(api, admins, tokenService, redis);
        fixtures.clearRateLimits();
    }

    @Test
    void teacherPostReachesTheControllerWithoutACsrfToken() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(fixtures.adminToken());
        assertThat(api.post("/api/ai/score-answer", VALID_SCORE_BODY).bearer(teacher.accessToken()).send().status())
                .isEqualTo(202);
    }

    @Test
    void missingTokenIsRejectedWithTokenMissing() {
        api.get("/api/ai/tasks/33333333-3333-3333-3333-333333333333").send().expect(401, "TOKEN_MISSING");
        api.get("/admin/files").send().expect(401, "TOKEN_MISSING");
    }

    @Test
    void unknownOrMalformedTokensAreInvalid() {
        api.get("/admin/files").bearer("adt_not-issued").send().expect(401, "TOKEN_INVALID");
        api.get("/admin/files").bearer("no-prefix").send().expect(401, "TOKEN_INVALID");
    }

    @Test
    void expiredTokenIsReportedAsExpired() {
        String token = "at_expired-" + AuthFixtures.uniqueName("");
        store.saveTeacher(Tokens.sha256Hex(token),
                new RedisTokenStore.TeacherGrant(1, Instant.now().minusSeconds(60)), java.time.Duration.ofMinutes(1));
        api.get("/api/ai/tasks/33333333-3333-3333-3333-333333333333").bearer(token).send()
                .expect(401, "TOKEN_EXPIRED");
    }

    @Test
    void adminOnlyPathsRejectTeacherTokens() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(fixtures.adminToken());
        api.get("/admin/files").bearer(teacher.accessToken()).send().expect(403, "AUTH_ROLE_MISMATCH");
        api.get("/admin/users").bearer(teacher.accessToken()).send().expect(403, "AUTH_ROLE_MISMATCH");
    }

    @Test
    void teacherOnlyPathsRejectAdminTokens() {
        api.get("/api/ai/tasks/33333333-3333-3333-3333-333333333333").bearer(fixtures.adminToken()).send()
                .expect(403, "AUTH_ROLE_MISMATCH");
    }

    @Test
    void sharedPathsAcceptBothRoles() {
        String adminToken = fixtures.adminToken();
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        // 通过安全链后由业务决定结果：不存在的文件 404，而不是 401/403
        assertThat(api.get("/api/files/CF_NOT_EXIST").bearer(adminToken).send().status()).isEqualTo(404);
        assertThat(api.get("/api/files/CF_NOT_EXIST").bearer(teacher.accessToken()).send().status()).isEqualTo(404);
        assertThat(api.get("/api/ai/rubrics").bearer(adminToken).send().status()).isEqualTo(200);
    }

    @Test
    void adminPathsAcceptAdminTokens() {
        assertThat(api.get("/admin/files").bearer(fixtures.adminToken()).send().status()).isEqualTo(200);
    }

    @Test
    void publicEndpointsNeedNoToken() {
        // 免登录接口不能因为没带 Token 被挡成 TOKEN_MISSING，要走到业务判断
        fixtures.refresh("rt_unknown", HttpApi.newKey()).expect(401, "REFRESH_TOKEN_INVALID");
        api.post("/admin/login", "{\"username\":\"nobody_here\",\"password\":\"wrong-password\"}").send()
                .expect(401, "INVALID_CREDENTIALS");
        fixtures.register("0000000000000000", AuthFixtures.uniqueName("t_")).expect(409, "LICENSE_UNAVAILABLE");
    }

    @Test
    void publicEndpointsIgnoreAStaleBearerHeader() {
        // 客户端带着过期 access 去刷新是常态，不能因此被拦
        api.post("/api/auth/refresh", "{\"refresh_token\":\"rt_unknown\"}")
                .bearer("at_stale").idempotencyKey(HttpApi.newKey()).send()
                .expect(401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void pathsOutsideTheContractAreDenied() {
        api.get("/something-else").send().expect(401, "TOKEN_MISSING");
        assertThat(api.get("/something-else").bearer(fixtures.adminToken()).send().status()).isEqualTo(403);
    }
}
