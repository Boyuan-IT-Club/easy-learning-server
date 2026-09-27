package com.earlylearning.early_learning_server.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全链的对外行为 —— 走**真实 HTTP**，不是 MockMvc standalone。
 *
 * <p>这条测试守的是一个真实踩过的坑：Spring Security 默认开启 CSRF，`CsrfFilter` 会在认证之前
 * 拒掉不安全方法，于是 `/api/**` 的 **POST 一律 401 而 GET 正常**；而契约里的调用方是非浏览器
 * 客户端，没有取 CSRF 令牌这一步。用 standalone MockMvc 测不出来——那套不带安全过滤器。
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

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void apiPostReachesTheControllerWithoutACsrfToken() throws Exception {
        assertThat(post("/api/ai/score-answer", VALID_SCORE_BODY)).isEqualTo(202);
    }

    @Test
    void apiGetDoesNotRequireAuthentication() throws Exception {
        assertThat(get("/api/ai/tasks/33333333-3333-3333-3333-333333333333")).isEqualTo(404);
    }

    @Test
    void adminEndpointsReachTheControllerWithoutAuthentication() throws Exception {
        // 与 /api/** 同链：Controller 本身不校验权限（全局约定），所以这里不该再被安全层挡住
        assertThat(get("/admin/files")).isEqualTo(200);
    }

    @Test
    void pathsOutsideThoseChainsStillRequireAuthentication() throws Exception {
        // 其余路径保留默认的会话 + CSRF
        assertThat(get("/something-else")).isEqualTo(401);
    }

    private int get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private int post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private int send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + context.getWebServer().getPort() + path);
    }
}
