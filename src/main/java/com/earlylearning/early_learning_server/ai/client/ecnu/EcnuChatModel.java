package com.earlylearning.early_learning_server.ai.client.ecnu;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ECNU（ChatECNU）实现：走 OpenAI 兼容的 {@code /chat/completions}。
 *
 * <p>两处与厂商能力相关的处理：
 * <ul>
 *   <li><b>模型按是否带图选</b>：{@code ecnu-max} 不支持图片理解，带图必须用 {@code ecnu-plus}。
 *       这条规则在这里，调用方不需要知道；</li>
 *   <li><b>图片走 data URI</b>：{@code image_url.url = "data:<mime>;base64,<...>"}，
 *       所以调用方给的是字节，不是链接。</li>
 * </ul>
 *
 * <p>失败一律抛 {@link AiTaskFailedException}（与其他出站端口同一套错误处理，不再自带异常类型）：
 * 429（并发配额）、5xx、超时与 IO 归可重试；4xx（除 429）与响应结构不对归不可重试。
 * 响应本身不合法用 {@code MODEL_OUTPUT_INVALID}，传输层问题用 {@code MODEL_TIMEOUT}。
 */
public class EcnuChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(EcnuChatModel.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EcnuProperties ecnuProperties;
    private final HttpClient http;

    public EcnuChatModel(EcnuProperties ecnuProperties) {
        this.ecnuProperties = ecnuProperties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, ecnuProperties.timeoutSeconds())))
                .build();
    }

    /** 带图走视觉模型，不带图走文本模型——这是本适配器唯一关心"能力差异"的地方。 */
    @Override
    public String modelFor(ChatRequest request) {
        return request.hasImages() ? ecnuProperties.modelVision() : ecnuProperties.modelText();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        String model = modelFor(request);
        Map<String, Object> body = buildBody(request, ecnuProperties, model);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(ecnuProperties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(ecnuProperties.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ecnuProperties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT,
                    "调用 ECNU 失败：" + ex.getClass().getSimpleName(), true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "调用 ECNU 被中断", true, ex);
        }

        int status = response.statusCode();
        if (status != 200) {
            // 并发配额是每用户每模型 3，超出直接 429；5xx 与限流都值得重试
            boolean retryable = status == 429 || status >= 500;
            log.warn("ECNU 返回非 200 status={} model={} retryable={}", status, model, retryable);
            throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT, "ECNU 返回 " + status, retryable);
        }
        return new ChatResponse(extractContent(response.body()), model);
    }

    /**
     * 构造请求体。独立出来是为了能离线验证形状（模型选择、data URI、response_format、思考模式），
     * 不需要真的发出请求。
     */
    static Map<String, Object> buildBody(ChatRequest request, EcnuProperties ecnuProperties, String model) {
        if (request.images() != null) {
            for (ImagePart image : request.images()) {
                if (image.content() != null && image.content().length > ecnuProperties.maxImageBytes()) {
                    throw new AiTaskFailedException(TaskFailureCode.MODEL_TIMEOUT,
                        "单张图片超过上限 " + ecnuProperties.maxImageBytes() + " 字节", false);
                }
            }
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            if (!"user".equals(message.role()) || !request.hasImages()) {
                // 无图时 user 也用纯文本，保持与文档示例一致
                messages.add(Map.of("role", message.role(), "content", message.text()));
                continue;
            }
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", message.text()));
            for (ImagePart image : request.images()) {
                parts.add(Map.of("type", "image_url",
                        "image_url", Map.of("url", dataUri(image))));
            }
            messages.add(Map.of("role", "user", "content", parts));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        if (request.schemaJson() != null && !request.schemaJson().isBlank()) {
            body.put("response_format", Map.of("type", "json_schema",
                    "json_schema", Map.of("name", request.schemaName() == null ? "result" : request.schemaName(),
                            "schema", readJson(request.schemaJson()))));
        }
        if (ecnuProperties.thinking()) {
            body.put("thinking", Map.of("type", "enabled"));
            if (ecnuProperties.reasoningEffort() != null && !ecnuProperties.reasoningEffort().isBlank()) {
                body.put("reasoning_effort", ecnuProperties.reasoningEffort());
            }
        }
        return body;
    }

    private static String dataUri(ImagePart image) {
        return "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.content());
    }

    private static String extractContent(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AiTaskFailedException(TaskFailureCode.MODEL_OUTPUT_INVALID, "ECNU 响应里没有内容", false);
            }
            return content.asText();
        } catch (AiTaskFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiTaskFailedException(TaskFailureCode.MODEL_OUTPUT_INVALID,
                    "ECNU 响应无法解析：" + ex.getClass().getSimpleName(), false, ex);
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            // 请求体由我们自己拼装，序列化失败说明代码有问题，不是"模型调用失败"：
        // 按项目约定归编程错误（IllegalStateException），执行器会落成阶段默认失败码并留下类型名
        throw new IllegalStateException("请求体序列化失败", ex);
        }
    }

    private static Object readJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception ex) {
            // 同上：Schema 是我们自己给的常量，解析不了是编程错误
        throw new IllegalStateException("输出 Schema 不是合法 JSON", ex);
        }
    }
}
