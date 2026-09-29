package com.earlylearning.early_learning_server.common.web;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证响应包络的序列化形状与契约一致。
 *
 * <p>两条契约约束在此钉住：失败时 {@code data} 必须是显式 null；
 * 成功时不能出现 {@code details} 键（成功 schema 无该属性且 additionalProperties:false）。
 */
class ApiResponseTests {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void successHasOkCodeAndDataWithoutDetails() {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                ApiResponse.ok(Map.of("file_code", "CF_a1b2"))));

        assertThat(json.get("code").asString()).isEqualTo("OK");
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").get("file_code").asString()).isEqualTo("CF_a1b2");
        assertThat(json.has("details")).isFalse();
    }

    @Test
    void failureExposesNullData() {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.RESOURCE_IN_USE)));

        assertThat(json.get("code").asString()).isEqualTo("RESOURCE_IN_USE");
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.has("details")).isFalse();
    }

    @Test
    void detailsUsesSnakeCaseAndOmitsNulls() {
        JsonNode details = mapper.readTree(mapper.writeValueAsString(
                        ApiResponse.fail(ErrorCode.RESOURCE_IN_USE, null,
                                ApiErrorDetails.atFile("CF_a1b2"))))
                .get("details");

        assertThat(details.get("file_code").asString()).isEqualTo("CF_a1b2");
        //  minProperties:1，其余字段为 null 时必须被省略
        assertThat(details.size()).isEqualTo(1);
    }

    @Test
    void limitIsNestedObjectWithWireCaseName() {
        JsonNode limit = mapper.readTree(mapper.writeValueAsString(
                        ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE, null,
                                ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.DURATION_MS, 600_000))))
                .get("details")
                .get("limit");

        assertThat(limit.get("name").asString()).isEqualTo("duration_ms");
        assertThat(limit.get("maximum").asLong()).isEqualTo(600_000L);
    }

    @Test
    void failureMessageFallsBackToCodeDefault() {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.FILE_DELETED)));

        assertThat(json.get("message").asString())
                .isEqualTo(ErrorCode.FILE_DELETED.defaultMessage());
    }
}
