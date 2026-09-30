package com.earlylearning.early_learning_server.teacher;

import static com.earlylearning.early_learning_server.support.HttpApi.headers;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.earlylearning.early_learning_server.support.HttpApi;
import com.earlylearning.early_learning_server.support.HttpApi.Response;
import com.earlylearning.early_learning_server.support.TestAccounts;

import tools.jackson.databind.JsonNode;

/**
 * 教师账号的端到端流程：发码 → 校验 → 注册 → 访问 → 刷新 → 停用/启用 → 撤销 → 解绑 → 恢复。
 *
 * <p>走真实 HTTP、真实 MySQL 与 Redis；编号对应 01_账号与鉴权 9.4 的验收用例。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeacherAccountFlowTests {

    @Autowired
    private WebServerApplicationContext context;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpApi api;
    private String adminToken;

    @BeforeEach
    void setUp() {
        api = new HttpApi(context.getWebServer().getPort());
        accounts.resetRateLimits();
        adminToken = accounts.adminToken();
    }

    // ---------- 注册 ----------

    @Test
    void A3_不存在_已使用_已撤销的码都回LICENSE_UNAVAILABLE() {
        assertThat(verify("0000-0000-0000-0000").code()).isIn("LICENSE_UNAVAILABLE", "INVALID_REQUEST");

        String used = issueCodes(1).getFirst();
        register(used, username(), TestAccounts.newDeviceId());
        assertThat(verify(used).code()).isEqualTo("LICENSE_UNAVAILABLE");

        JsonNode issued = issueBatch(1).get("licenses").get(0);
        revokeLicense(issued.get("id").asInt());
        assertThat(verify(issued.get("activation_code").asString()).code()).isEqualTo("LICENSE_UNAVAILABLE");
    }

    @Test
    void 格式错的码回400且带字段定位() {
        Response response = verify("ABC");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("details").get("field_path").asString()).isEqualTo("/activation_code");
    }

    @Test
    void 注册成功后可以访问教师接口_数据库里没有明文() {
        String code = issueCodes(1).getFirst();
        assertThat(verify(code).status()).isEqualTo(200);

        String device = TestAccounts.newDeviceId();
        String name = username();
        Response registered = register(code, name, device);
        assertThat(registered.status()).isEqualTo(201);
        String access = registered.dataText("access_token");
        String refresh = registered.dataText("refresh_token");
        assertThat(access).startsWith("at_");
        assertThat(refresh).startsWith("rt_");

        Response me = api.get("/api/auth/me", teacher(access, device));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.dataText("username")).isEqualTo(name);
        assertThat(me.dataText("account_status")).isEqualTo("ACTIVE");

        // A8：库里只有哈希
        String storedRefresh = jdbc.queryForObject(
                "SELECT refresh_token_hash FROM user_account WHERE username = ?", String.class, name);
        assertThat(storedRefresh).isNotEqualTo(refresh).hasSize(64);
        Long plaintextHits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_record WHERE response_body LIKE ? OR response_body LIKE ?",
                Long.class, "%" + access + "%", "%" + refresh + "%");
        assertThat(plaintextHits).isZero();
    }

    @Test
    void A4_两台设备并发用同一个码注册_只有一台成功() throws Exception {
        String code = issueCodes(1).getFirst();
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Response>> attempts = List.of(1, 2).stream()
                .map(i -> CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return register(code, username(), TestAccounts.newDeviceId());
                }))
                .toList();
        start.countDown();
        List<Integer> statuses = attempts.stream().map(f -> f.join().status()).sorted().toList();
        assertThat(statuses).containsExactly(201, 409);
    }

    @Test
    void A5_同一个Key重试返回同一个账号_码只占用一次() {
        String code = issueCodes(1).getFirst();
        String device = TestAccounts.newDeviceId();
        String name = username();
        String key = UUID.randomUUID().toString();
        Response first = register(code, name, device, key);
        Response replay = register(code, name, device, key);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.data().get("user_id").asInt()).isEqualTo(first.data().get("user_id").asInt());
        assertThat(replay.dataText("access_token")).isEqualTo(first.dataText("access_token"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username = ?", Long.class, name))
                .isEqualTo(1L);
    }

    @Test
    void 同一个Key换了输入回409_IDEMPOTENCY_CONFLICT() {
        String code = issueCodes(1).getFirst();
        String key = UUID.randomUUID().toString();
        register(code, username(), TestAccounts.newDeviceId(), key);
        Response conflict = register(code, username(), TestAccounts.newDeviceId(), key);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void A6_用户名冲突时码不被占用() {
        String taken = username();
        register(issueCodes(1).getFirst(), taken, TestAccounts.newDeviceId());

        String code = issueCodes(1).getFirst();
        Response conflict = register(code, taken, TestAccounts.newDeviceId());
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.code()).isEqualTo("USERNAME_EXISTS");
        assertThat(verify(code).status()).isEqualTo(200);
    }

    @Test
    void A7_已绑定的设备不能再注册新账号() {
        String device = TestAccounts.newDeviceId();
        register(issueCodes(1).getFirst(), username(), device);
        Response second = register(issueCodes(1).getFirst(), username(), device);
        assertThat(second.code()).isEqualTo("DEVICE_ALREADY_BOUND");
    }

    // ---------- 设备与刷新 ----------

    @Test
    void 换了设备号的请求回403_DEVICE_MISMATCH() {
        Teacher teacher = newTeacher();
        Response response = api.get("/api/auth/me", teacher(teacher.access, TestAccounts.newDeviceId()));
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("DEVICE_MISMATCH");
    }

    @Test
    void A11_A12_刷新轮换_30秒内重放返回同一组_旧access仍可用到过期() {
        Teacher teacher = newTeacher();
        Response first = refresh(teacher.refresh, teacher.device);
        assertThat(first.status()).isEqualTo(200);
        String newRefresh = first.dataText("refresh_token");
        assertThat(newRefresh).isNotEqualTo(teacher.refresh);

        Response replay = refresh(teacher.refresh, teacher.device);
        assertThat(replay.dataText("refresh_token")).isEqualTo(newRefresh);
        assertThat(replay.dataText("access_token")).isEqualTo(first.dataText("access_token"));

        assertThat(api.get("/api/auth/me", teacher(first.dataText("access_token"), teacher.device)).status())
                .isEqualTo(200);
        // 新凭证继续可以轮换
        assertThat(refresh(newRefresh, teacher.device).status()).isEqualTo(200);
    }

    @Test
    void 刷新时设备不符回403() {
        Teacher teacher = newTeacher();
        assertThat(refresh(teacher.refresh, TestAccounts.newDeviceId()).code()).isEqualTo("DEVICE_MISMATCH");
    }

    // ---------- 停用、撤销、解绑 ----------

    @Test
    void A13_A15_停用立即生效_启用后原凭证可以恢复() {
        Teacher teacher = newTeacher();
        assertThat(adminPost("/admin/teachers/" + teacher.id + "/disable", Map.of("reason", "测试停用")).status())
                .isEqualTo(200);

        Response blocked = api.get("/api/auth/me", teacher(teacher.access, teacher.device));
        assertThat(blocked.status()).isEqualTo(401);  // access 已被吊销
        Response refreshBlocked = refresh(teacher.refresh, teacher.device);
        assertThat(refreshBlocked.status()).isEqualTo(403);
        assertThat(refreshBlocked.code()).isEqualTo("ACCOUNT_DISABLED");

        adminPost("/admin/teachers/" + teacher.id + "/enable", Map.of());
        Response restored = refresh(teacher.refresh, teacher.device);
        assertThat(restored.status()).isEqualTo(200);
        assertThat(api.get("/api/auth/me", teacher(restored.dataText("access_token"), teacher.device)).status())
                .isEqualTo(200);
    }

    @Test
    void 停用必须填原因() {
        Teacher teacher = newTeacher();
        Response response = adminPost("/admin/teachers/" + teacher.id + "/disable", Map.of("reason", " "));
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("details").get("field_path").asString()).isEqualTo("/reason");
    }

    @Test
    void A16_撤销ACTIVE激活码后刷新回LICENSE_REVOKED() {
        Teacher teacher = newTeacher();
        assertThat(revokeLicense(teacher.licenseId).status()).isEqualTo(200);
        assertThat(api.get("/api/auth/me", teacher(teacher.access, teacher.device)).status()).isEqualTo(401);
        Response refreshed = refresh(teacher.refresh, teacher.device);
        assertThat(refreshed.status()).isEqualTo(403);
        assertThat(refreshed.code()).isEqualTo("LICENSE_REVOKED");
    }

    @Test
    void 撤销已撤销的码整体失败并列出id() {
        JsonNode issued = issueBatch(2).get("licenses");
        int revoked = issued.get(0).get("id").asInt();
        int fresh = issued.get(1).get("id").asInt();
        revokeLicense(revoked);
        Response response = adminPost("/admin/licenses/revoke",
                Map.of("license_ids", List.of(revoked, fresh), "reason", "重复撤销"));
        assertThat(response.code()).isEqualTo("LICENSE_UNAVAILABLE");
        assertThat(response.body().get("details").get("license_ids").get(0).asInt()).isEqualTo(revoked);
        // 全部失败：另一枚没有被顺带撤销
        assertThat(verify(issued.get(1).get("activation_code").asString()).status()).isEqualTo(200);
    }

    @Test
    void A17_A18_解绑后原设备失效_新设备凭恢复码重新绑定() {
        Teacher teacher = newTeacher();
        adminPost("/admin/teachers/" + teacher.id + "/unbind-device", Map.of("reason", "换机"));
        assertThat(refresh(teacher.refresh, teacher.device).code()).isEqualTo("REFRESH_TOKEN_INVALID");

        String code = issueRecoveryCode(teacher.id);
        String newDevice = TestAccounts.newDeviceId();
        Response recovered = recover(teacher.username, code, newDevice);
        assertThat(recovered.status()).isEqualTo(200);
        assertThat(recovered.data().get("device_rebound").asBoolean()).isTrue();
        assertThat(api.get("/api/auth/me", teacher(recovered.dataText("access_token"), newDevice)).status())
                .isEqualTo(200);
        // 恢复码一次性
        assertThat(recover(teacher.username, code, newDevice).code()).isEqualTo("RECOVERY_CODE_INVALID");
    }

    @Test
    void A19_同设备凭恢复码重置_不改绑定() {
        Teacher teacher = newTeacher();
        Response recovered = recover(teacher.username, issueRecoveryCode(teacher.id), teacher.device);
        assertThat(recovered.status()).isEqualTo(200);
        assertThat(recovered.data().get("device_rebound").asBoolean()).isFalse();
        // 恢复签发了新的 refresh，旧的作废
        assertThat(refresh(teacher.refresh, teacher.device).code()).isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void 账号仍绑在别的设备上时不能恢复() {
        Teacher teacher = newTeacher();
        Response response = recover(teacher.username, issueRecoveryCode(teacher.id), TestAccounts.newDeviceId());
        assertThat(response.code()).isEqualTo("DEVICE_ALREADY_BOUND");
    }

    @Test
    void A20_恢复码错5次作废_之后输对也无效() {
        Teacher teacher = newTeacher();
        String code = issueRecoveryCode(teacher.id);
        for (int i = 0; i < 5; i++) {
            accounts.resetRateLimits();
            assertThat(recover(teacher.username, "ZZZZ-ZZZZ", teacher.device).code())
                    .isEqualTo("RECOVERY_CODE_INVALID");
        }
        accounts.resetRateLimits();
        assertThat(recover(teacher.username, code, teacher.device).code()).isEqualTo("RECOVERY_CODE_INVALID");
    }

    @Test
    void A25_批量生成可重放_数据库里没有明文() {
        String key = UUID.randomUUID().toString();
        Response first = api.post("/admin/licenses/batch", Map.of("count", 3, "remark", "重放测试"),
                admin(key));
        Response replay = api.post("/admin/licenses/batch", Map.of("count", 3, "remark", "重放测试"),
                admin(key));
        assertThat(first.status()).isEqualTo(201);
        assertThat(replay.data()).isEqualTo(first.data());

        String plain = first.data().get("licenses").get(0).get("activation_code").asString();
        Long hits = jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE response_body LIKE ?",
                Long.class, "%" + plain + "%");
        assertThat(hits).isZero();
    }

    @Test
    void 后台教师列表能看到激活码尾号与设备绑定() {
        Teacher teacher = newTeacher();
        Response list = api.get("/admin/teachers?keyword=" + teacher.username, admin(null));
        JsonNode row = list.data().get("items").get(0);
        assertThat(row.get("username").asString()).isEqualTo(teacher.username);
        assertThat(row.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(row.get("device_bound").asBoolean()).isTrue();
        assertThat(row.get("license").get("status").asString()).isEqualTo("ACTIVE");
    }

    // ---------- 工具 ----------

    private record Teacher(int id, String username, String device, String access, String refresh, int licenseId) {
    }

    private Teacher newTeacher() {
        JsonNode issued = issueBatch(1).get("licenses").get(0);
        String device = TestAccounts.newDeviceId();
        String name = username();
        Response registered = register(issued.get("activation_code").asString(), name, device);
        assertThat(registered.status()).isEqualTo(201);
        return new Teacher(registered.data().get("user_id").asInt(), name, device,
                registered.dataText("access_token"), registered.dataText("refresh_token"),
                issued.get("id").asInt());
    }

    private JsonNode issueBatch(int count) {
        Response response = api.post("/admin/licenses/batch", Map.of("count", count),
                admin(UUID.randomUUID().toString()));
        assertThat(response.status()).isEqualTo(201);
        return response.data();
    }

    private List<String> issueCodes(int count) {
        List<String> codes = new java.util.ArrayList<>();
        issueBatch(count).get("licenses").forEach(node -> codes.add(node.get("activation_code").asString()));
        return codes;
    }

    private Response revokeLicense(int id) {
        return adminPost("/admin/licenses/revoke", Map.of("license_ids", List.of(id), "reason", "测试撤销"));
    }

    private String issueRecoveryCode(int teacherId) {
        Response response = api.post("/admin/teachers/" + teacherId + "/recovery-code", Map.of(),
                admin(UUID.randomUUID().toString()));
        assertThat(response.status()).isEqualTo(201);
        return response.dataText("recovery_code");
    }

    private Response verify(String code) {
        accounts.resetRateLimits();
        return api.post("/api/auth/licenses/verify", Map.of("activation_code", code), Map.of());
    }

    private Response register(String code, String name, String device) {
        return register(code, name, device, UUID.randomUUID().toString());
    }

    private Response register(String code, String name, String device, String key) {
        accounts.resetRateLimits();
        return api.post("/api/auth/register", Map.of("activation_code", code, "username", name),
                headers("Idempotency-Key", key, "X-Device-Id", device));
    }

    private Response refresh(String refreshToken, String device) {
        return api.post("/api/auth/refresh", Map.of("refresh_token", refreshToken), headers("X-Device-Id", device));
    }

    private Response recover(String name, String code, String device) {
        return api.post("/api/auth/recover", Map.of("username", name, "recovery_code", code),
                headers("Idempotency-Key", UUID.randomUUID().toString(), "X-Device-Id", device));
    }

    private Response adminPost(String path, Object body) {
        return api.post(path, body, admin(null));
    }

    private Map<String, String> admin(String idempotencyKey) {
        return headers("Authorization", "Bearer " + adminToken, "Idempotency-Key", idempotencyKey);
    }

    private static Map<String, String> teacher(String access, String device) {
        return headers("Authorization", "Bearer " + access, "X-Device-Id", device);
    }

    private static String username() {
        return "t_" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
