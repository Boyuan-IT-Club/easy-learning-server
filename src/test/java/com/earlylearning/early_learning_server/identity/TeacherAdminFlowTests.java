package com.earlylearning.early_learning_server.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.earlylearning.early_learning_server.identity.service.AdminAccountService;
import com.earlylearning.early_learning_server.security.service.TokenService;
import com.earlylearning.early_learning_server.support.AuthFixtures;
import com.earlylearning.early_learning_server.support.HttpApi;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 教师管理（契约 listTeachers、updateTeacherStatus）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeacherAdminFlowTests {

    private static final String SCORE_TASK = "/api/ai/tasks/33333333-3333-3333-3333-333333333333";

    @Autowired
    private WebServerApplicationContext context;
    @Autowired
    private AdminAccountService admins;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private StringRedisTemplate redis;

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
    void listMatchesLowercasedSubstringAndTreatsWildcardsLiterally() {
        String marker = AuthFixtures.uniqueName("m");
        String withUnderscore = "t_" + marker + "_x";
        String similar = "t_" + marker + "ax";
        fixtures.register(fixtures.issueLicense(adminToken).code(), withUnderscore).expect(201, "OK");
        fixtures.register(fixtures.issueLicense(adminToken).code(), similar).expect(201, "OK");

        JsonNode all = list("?username=" + marker.toUpperCase()).expect(200, "OK").conformsTo("listTeachers").data();
        assertThat(all.get("total").asLong()).isEqualTo(2);
        // 默认 id 降序
        assertThat(all.get("items").get(0).get("username").asString()).isEqualTo(similar);

        // "_" 按普通字符：不能把 "ax" 也匹配上
        JsonNode literal = list("?username=" + marker + "_").expect(200, "OK").data();
        assertThat(literal.get("total").asLong()).isEqualTo(1);
        assertThat(literal.get("items").get(0).get("username").asString()).isEqualTo(withUnderscore);

        assertThat(list("?username=" + marker + "%25").expect(200, "OK").data().get("total").asLong()).isZero();
    }

    @Test
    void listFiltersByStatusAndValidatesParameters() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        patchStatus(teacher.userId(), 0).expect(200, "OK");

        JsonNode disabled = list("?status=0&page_size=100").expect(200, "OK").conformsTo("listTeachers").data();
        disabled.get("items").forEach(item -> assertThat(item.get("status").asInt()).isZero());

        list("?status=2").expect(400, "INVALID_REQUEST").conformsTo("listTeachers");
        list("?page=0").expect(400, "INVALID_REQUEST");
        list("?page_size=0").expect(400, "INVALID_REQUEST");
    }

    @Test
    void disablingBlocksCloudAccessAndEnablingRestoresIt() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);

        JsonNode disabled = patchStatus(teacher.userId(), 0).expect(200, "OK").conformsTo("updateTeacherStatus").data();
        assertThat(disabled.get("status").asInt()).isZero();
        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(403, "ACCOUNT_DISABLED");

        // 重复提交相同目标状态：照常返回
        patchStatus(teacher.userId(), 0).expect(200, "OK");

        patchStatus(teacher.userId(), 1).expect(200, "OK");
        api.get(SCORE_TASK).bearer(teacher.accessToken()).send().expect(404, "TASK_NOT_FOUND");
    }

    @Test
    void enablingRequiresAnActiveLicense() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        api.post("/admin/licenses/" + teacher.licenseId() + "/revoke", null).bearer(adminToken).send()
                .expect(200, "OK");

        patchStatus(teacher.userId(), 1).expect(409, "LICENSE_REVOKED").conformsTo("updateTeacherStatus");
    }

    @Test
    void updateValidatesInput() {
        AuthFixtures.Teacher teacher = fixtures.newTeacher(adminToken);
        patchStatus(teacher.userId(), 2).expect(400, "INVALID_REQUEST").conformsTo("updateTeacherStatus");
        api.patch("/admin/users/" + teacher.userId() + "/status", "{}").bearer(adminToken).send()
                .expect(400, "INVALID_REQUEST");
        api.patch("/admin/users/" + teacher.userId() + "/status", "{\"status\":1,\"x\":1}").bearer(adminToken).send()
                .expect(400, "INVALID_REQUEST");
        patchStatus(999999999, 1).expect(404, "RESOURCE_NOT_FOUND").conformsTo("updateTeacherStatus");
    }

    private HttpApi.Response list(String query) {
        return api.get("/admin/users" + query).bearer(adminToken).send();
    }

    private HttpApi.Response patchStatus(int userId, int status) {
        return api.patch("/admin/users/" + userId + "/status", "{\"status\":" + status + "}").bearer(adminToken).send();
    }
}
