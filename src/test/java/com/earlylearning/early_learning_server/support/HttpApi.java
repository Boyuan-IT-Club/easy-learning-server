package com.earlylearning.early_learning_server.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 走真实 HTTP 调接口（经过完整的安全过滤链），并可按契约校验响应体。
 *
 * <p>不用 standalone MockMvc：那套不带安全过滤器，测不出认证与角色规则。
 */
public final class HttpApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newHttpClient();
    private final int port;

    public HttpApi(int port) {
        this.port = port;
    }

    public Call get(String path) {
        return new Call("GET", path, null);
    }

    public Call post(String path, String body) {
        return new Call("POST", path, body);
    }

    public Call patch(String path, String body) {
        return new Call("PATCH", path, body);
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** 一次请求的构建器。 */
    public final class Call {

        private final HttpRequest.Builder builder;

        private Call(String method, String path, String body) {
            builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
        }

        public Call bearer(String token) {
            builder.header("Authorization", "Bearer " + token);
            return this;
        }

        public Call idempotencyKey(String key) {
            builder.header("Idempotency-Key", key);
            return this;
        }

        public Response send() {
            try {
                HttpResponse<String> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return new Response(response.statusCode(), response.body());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /** 响应：状态码与原始响应体。 */
    public record Response(int status, String body) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public JsonNode data() {
            return json().get("data");
        }

        public String code() {
            return json().path("code").asString();
        }

        /** 断言状态码与业务码。 */
        public Response expect(int expectedStatus, String expectedCode) {
            assertThat(status).as("HTTP 状态，响应体：%s", body).isEqualTo(expectedStatus);
            assertThat(code()).as("业务码，响应体：%s", body).isEqualTo(expectedCode);
            return this;
        }

        /** 断言响应体符合契约中该操作、该状态码的 schema。 */
        public Response conformsTo(String operationId) {
            assertThat(ContractSchema.violations(operationId, status, body))
                    .as("%s %d 响应与契约不符：%s", operationId, status, body)
                    .isEmpty();
            return this;
        }
    }
}
