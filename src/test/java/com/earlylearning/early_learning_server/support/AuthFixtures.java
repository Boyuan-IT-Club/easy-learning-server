package com.earlylearning.early_learning_server.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.earlylearning.early_learning_server.admin.domain.AdminAccountSummary;
import com.earlylearning.early_learning_server.admin.application.AdminAccountService;
import com.earlylearning.early_learning_server.auth.application.TokenService;

import tools.jackson.databind.JsonNode;

/**
 * 鉴权相关测试的公共准备：建管理员拿 Token、发激活码、注册教师。
 *
 * <p>管理员 Token 直接签发，不走登录接口：登录有按 IP 的限流，测试多了会互相干扰。
 * 注册与登录本身的限流在各自的测试里单独覆盖。
 */
public final class AuthFixtures {

    public static final String ADMIN_PASSWORD = "Admin-Pass-0001";

    private final HttpApi api;
    private final AdminAccountService admins;
    private final TokenService tokens;
    private final StringRedisTemplate redis;

    public AuthFixtures(HttpApi api, AdminAccountService admins, TokenService tokens, StringRedisTemplate redis) {
        this.api = api;
        this.admins = admins;
        this.tokens = tokens;
        this.redis = redis;
    }

    public static String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 新建一个 ACTIVE 管理员。 */
    public AdminAccountSummary newAdmin() {
        return admins.bootstrap(uniqueName("admin_"), ADMIN_PASSWORD);
    }

    public String adminToken(AdminAccountSummary admin) {
        return tokens.issueAdmin(admin.id()).value();
    }

    public String adminToken() {
        return adminToken(newAdmin());
    }

    /** 通过接口生成激活码。 */
    public List<IssuedCode> issueLicenses(String adminToken, int count) {
        HttpApi.Response response = api.post("/admin/licenses", "{\"count\":" + count + "}")
                .bearer(adminToken).idempotencyKey(HttpApi.newKey()).send()
                .expect(201, "OK");
        List<IssuedCode> codes = new ArrayList<>();
        for (JsonNode item : response.data().get("items")) {
            codes.add(new IssuedCode(item.get("id").asInt(), item.get("activation_code").asString()));
        }
        return codes;
    }

    public IssuedCode issueLicense(String adminToken) {
        return issueLicenses(adminToken, 1).getFirst();
    }

    public HttpApi.Response register(String activationCode, String username) {
        return register(activationCode, username, HttpApi.newKey());
    }

    public HttpApi.Response register(String activationCode, String username, String key) {
        return api.post("/api/auth/register",
                        "{\"activation_code\":\"" + activationCode + "\",\"username\":\"" + username + "\"}")
                .idempotencyKey(key).send();
    }

    /** 注册一个可用的教师，返回其凭证。 */
    public Teacher newTeacher(String adminToken) {
        IssuedCode code = issueLicense(adminToken);
        JsonNode data = register(code.code(), uniqueName("t_")).expect(201, "OK").data();
        return new Teacher(data.get("user").get("id").asInt(), code.id(),
                data.get("access_token").asString(), data.get("refresh_token").asString());
    }

    public HttpApi.Response refresh(String refreshToken, String key) {
        return api.post("/api/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}")
                .idempotencyKey(key).send();
    }

    /** 清掉限流与锁定计数，避免测试之间互相影响。 */
    public void clearRateLimits() {
        Set<String> keys = redis.keys("el:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public record IssuedCode(int id, String code) {
    }

    public record Teacher(int userId, int licenseId, String accessToken, String refreshToken) {
    }
}
