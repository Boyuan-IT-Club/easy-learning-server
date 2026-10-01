package com.earlylearning.early_learning_server.identity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.earlylearning.early_learning_server.identity.service.AdminAccountService;
import com.earlylearning.early_learning_server.security.service.TokenService;
import com.earlylearning.early_learning_server.common.secret.KeyedHasher;
import com.earlylearning.early_learning_server.support.AuthFixtures;
import com.earlylearning.early_learning_server.support.HttpApi;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 激活码管理（契约 createLicenses、listLicenses、revokeLicense）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LicenseFlowTests {

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
    @Autowired
    private KeyedHasher hasher;

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

    @Test
    void createIssuesDistinctCodesAndStoresOnlyHashes() {
        HttpApi.Response response = create(3, HttpApi.newKey()).expect(201, "OK").conformsTo("createLicenses");

        Set<String> codes = new HashSet<>();
        for (JsonNode item : response.data().get("items")) {
            String code = item.get("activation_code").asString();
            assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{16}");
            assertThat(item.get("status").asString()).isEqualTo("UNUSED");
            codes.add(code);

            String stored = jdbc.queryForObject("SELECT activation_code_hash FROM user_license WHERE id = ?",
                    String.class, item.get("id").asInt());
            assertThat(stored).isEqualTo(hasher.hash(code)).isNotEqualTo(code);
        }
        assertThat(codes).hasSize(3);
    }

    @Test
    void sameKeyReplaysAndDoesNotIssueMoreCodes() {
        String key = HttpApi.newKey();
        HttpApi.Response first = create(2, key).expect(201, "OK");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM user_license", Long.class);

        assertThat(create(2, key).expect(201, "OK").body()).isEqualTo(first.body());
        create(3, key).expect(409, "IDEMPOTENCY_CONFLICT").conformsTo("createLicenses");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_license", Long.class)).isEqualTo(before);
    }

    @Test
    void replayAfterCacheLossReportsTheCreatedIds() {
        String key = HttpApi.newKey();
        List<Integer> ids = new ArrayList<>();
        create(2, key).expect(201, "OK").data().get("items").forEach(item -> ids.add(item.get("id").asInt()));
        Set<String> keys = redis.keys("el:idem:sensitive:license.create:*");
        redis.delete(keys);

        JsonNode details = create(2, key).expect(409, "SENSITIVE_RESULT_EXPIRED").conformsTo("createLicenses")
                .json().get("details");
        List<Integer> reported = new ArrayList<>();
        details.get("license_ids").forEach(id -> reported.add(id.asInt()));
        assertThat(reported).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void countMustBeBetweenOneAndOneHundred() {
        create(0, HttpApi.newKey()).expect(400, "INVALID_REQUEST").conformsTo("createLicenses");
        create(101, HttpApi.newKey()).expect(400, "INVALID_REQUEST");
        assertThat(create(100, HttpApi.newKey()).expect(201, "OK").data().get("items").size()).isEqualTo(100);
        api.post("/admin/licenses", "{\"count\":1,\"note\":\"x\"}").bearer(adminToken)
                .idempotencyKey(HttpApi.newKey()).send().expect(400, "INVALID_REQUEST");
        api.post("/admin/licenses", "{\"count\":1}").bearer(adminToken).send().expect(400, "INVALID_REQUEST");
    }

    @Test
    void listFiltersByStatusAndUser() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);

        JsonNode byUser = api.get("/admin/licenses?user_id=" + teacher.userId()).bearer(adminToken).send()
                .expect(200, "OK").conformsTo("listLicenses").data();
        assertThat(byUser.get("total").asLong()).isEqualTo(1);
        JsonNode license = byUser.get("items").get(0);
        assertThat(license.get("id").asInt()).isEqualTo(teacher.licenseId());
        assertThat(license.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(license.get("activated_at").isNull()).isFalse();

        JsonNode unused = api.get("/admin/licenses?status=UNUSED&page_size=5").bearer(adminToken).send()
                .expect(200, "OK").conformsTo("listLicenses").data();
        assertThat(unused.get("page_size").asInt()).isEqualTo(5);
        unused.get("items").forEach(item -> {
            assertThat(item.get("status").asString()).isEqualTo("UNUSED");
            // 未使用时 user_id 与 activated_at 也要以 null 出现（契约 required）
            assertThat(item.has("user_id")).isTrue();
            assertThat(item.get("user_id").isNull()).isTrue();
            assertThat(item.get("activated_at").isNull()).isTrue();
        });

        api.get("/admin/licenses?status=USED").bearer(adminToken).send().expect(400, "INVALID_REQUEST")
                .conformsTo("listLicenses");
        api.get("/admin/licenses?page_size=101").bearer(adminToken).send().expect(400, "INVALID_REQUEST");
        api.get("/admin/licenses?user_id=0").bearer(adminToken).send().expect(400, "INVALID_REQUEST");
    }

    @Test
    void revokingAnActiveLicenseDisablesTheTeacherInTheSameStep() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);

        JsonNode revoked = api.post("/admin/licenses/" + teacher.licenseId() + "/revoke", null).bearer(adminToken)
                .send().expect(200, "OK").conformsTo("revokeLicense").data();

        assertThat(revoked.get("status").asString()).isEqualTo("REVOKED");
        assertThat(revoked.get("user_id").asInt()).isEqualTo(teacher.userId());
        assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE id = ?", Integer.class,
                teacher.userId())).isZero();
    }

    @Test
    void revokingIsIdempotentAndUnknownIdsAreNotFound() {
        int id = fixtures.issueLicense(adminToken).id();
        HttpApi.Response first = api.post("/admin/licenses/" + id + "/revoke", null).bearer(adminToken).send()
                .expect(200, "OK");
        HttpApi.Response again = api.post("/admin/licenses/" + id + "/revoke", null).bearer(adminToken).send()
                .expect(200, "OK");
        assertThat(again.data()).isEqualTo(first.data());

        api.post("/admin/licenses/999999999/revoke", null).bearer(adminToken).send()
                .expect(404, "RESOURCE_NOT_FOUND").conformsTo("revokeLicense");
    }

    private HttpApi.Response create(int count, String key) {
        return api.post("/admin/licenses", "{\"count\":" + count + "}").bearer(adminToken).idempotencyKey(key).send();
    }
}
