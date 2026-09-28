package com.earlylearning.early_learning_server.ai.adapter.ecnu;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.earlylearning.early_learning_server.ai.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.llm.ChatModel.ChatMessage;
import com.earlylearning.early_learning_server.ai.llm.ChatModel.ChatRequest;
import com.earlylearning.early_learning_server.ai.llm.ChatModel.ImagePart;
import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ECNU 适配器的**请求形状**（离线可验，不需要令牌）。
 *
 * <p>重点是一条产品规则：**不带图用 ecnu-max，带图必须用 ecnu-plus**——
 * 因为 ecnu-max 不支持图片理解（见平台文档的模型对比表）。
 */
class EcnuChatModelTests {

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"score\":{\"type\":\"integer\"}}}";

    private final EcnuProperties properties = new EcnuProperties(
            "https://chat.ecnu.edu.cn/open/api/v1", "test-key",
            "ecnu-max", "ecnu-plus", false, "", 120, 5242880);

    private final EcnuChatModel model = new EcnuChatModel(properties);

    @Test
    void textOnlyRequestUsesTheTextModel() {
        assertThat(model.modelFor(request(false))).isEqualTo("ecnu-max");
    }

    @Test
    void requestWithImagesUsesTheVisionModel() {
        assertThat(model.modelFor(request(true))).isEqualTo("ecnu-plus");
    }

    @Test
    void imagesBecomeDataUrisInAMultimodalUserMessage() {
        Map<String, Object> body = EcnuChatModel.buildBody(request(true), properties, "ecnu-plus");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages.get(0)).containsEntry("role", "system");
        Map<String, Object> user = messages.get(1);
        assertThat(user).containsEntry("role", "user");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) user.get("content");
        assertThat(parts.get(0)).containsEntry("type", "text").containsEntry("text", "题目与答案");
        assertThat(parts.get(1)).containsEntry("type", "image_url");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageUrl = (Map<String, Object>) parts.get(1).get("image_url");
        assertThat((String) imageUrl.get("url")).startsWith("data:image/png;base64,");
    }

    @Test
    void textOnlyRequestKeepsAFlatUserContent() {
        Map<String, Object> body = EcnuChatModel.buildBody(request(false), properties, "ecnu-max");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages.get(1)).containsEntry("content", "题目与答案");
    }

    @Test
    void schemaBecomesAJsonSchemaResponseFormat() {
        Map<String, Object> body = EcnuChatModel.buildBody(request(false), properties, "ecnu-max");

        @SuppressWarnings("unchecked")
        Map<String, Object> format = (Map<String, Object>) body.get("response_format");
        assertThat(format).containsEntry("type", "json_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) format.get("json_schema");
        assertThat(schema).containsEntry("name", "QuestionScore");
        assertThat(schema.get("schema")).isNotNull();
    }

    @Test
    void thinkingAndReasoningEffortAreOnlySentWhenEnabled() {
        assertThat(EcnuChatModel.buildBody(request(false), properties, "ecnu-max"))
                .doesNotContainKeys("thinking", "reasoning_effort");

        EcnuProperties thinking = new EcnuProperties(properties.baseUrl(), "k",
                "ecnu-max", "ecnu-plus", true, "high", 120, 5242880);
        Map<String, Object> body = EcnuChatModel.buildBody(request(false), thinking, "ecnu-max");
        assertThat(body).containsEntry("reasoning_effort", "high");
        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "enabled"));
    }

    @Test
    void oversizedImageIsRejectedBeforeCallingOut() {
        EcnuProperties tiny = new EcnuProperties(properties.baseUrl(), "k",
                "ecnu-max", "ecnu-plus", false, "", 120, 1024);
        ChatRequest request = new ChatRequest(List.of(new ChatMessage("user", "看这张图")),
                null, null, List.of(new ImagePart("image/png", new byte[2048])));

        assertThatThrownBy(() -> EcnuChatModel.buildBody(request, tiny, "ecnu-plus"))
                .isInstanceOf(AiTaskFailedException.class)
                .satisfies(ex -> assertThat(((AiTaskFailedException) ex).getFailureCode())
                        .isEqualTo(TaskFailureCode.MODEL_TIMEOUT))
                .hasMessageContaining("单张图片超过上限");
    }

    private ChatRequest request(boolean withImage) {
        return new ChatRequest(
                List.of(new ChatMessage("system", "评分规则"), new ChatMessage("user", "题目与答案")),
                "QuestionScore", SCHEMA,
                withImage ? List.of(new ImagePart("image/png", "图片字节".getBytes(StandardCharsets.UTF_8))) : List.of());
    }
}
