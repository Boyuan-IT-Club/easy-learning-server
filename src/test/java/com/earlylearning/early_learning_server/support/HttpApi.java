package com.earlylearning.early_learning_server.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 走真实 HTTP 调接口的小工具：安全链、过滤器、异常处理都参与，与平板和管理端的实际调用一致。
 */
public final class HttpApi {

    private static final JsonMapper JSON = new JsonMapper();

    private final HttpClient client = HttpClient.newHttpClient();
    private final int port;

    public HttpApi(int port) {
        this.port = port;
    }

    public Response get(String path, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(builder::header);
        return send(builder.build());
    }

    public Response post(String path, Object body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return send(builder.build());
    }

    public static Map<String, String> headers(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put(pairs[i], pairs[i + 1]);
            }
        }
        return map;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            JsonNode json = body == null || body.isBlank() ? null : JSON.readTree(body);
            return new Response(response.statusCode(), json);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** @param body 整个响应包络 {code, message, data, details?} */
    public record Response(int status, JsonNode body) {

        public String code() {
            return body == null ? null : body.get("code").asString();
        }

        public JsonNode data() {
            return body.get("data");
        }

        public String dataText(String field) {
            return data().get(field).asString();
        }
    }
}
