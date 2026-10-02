package com.earlylearning.early_learning_server.common.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.earlylearning.early_learning_server.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 {@link ErrorCode} 与接口契约绑死。
 *
 * <p>错误码是跨端契约，枚举取值必须与契约完全相同；契约新增错误码而未同步枚举时这里会失败。
 */
class ApiCodeTests {

    private static final String CONTRACT = "/contract/early-learning-api.openapi.json";

    /**
     * 契约中错误响应 schema 名 → 它对应的 HTTP 状态。
     *
     * <p>这张表必须覆盖契约里全部错误响应 schema，否则 {@link #契约里每个错误响应schema都已被覆盖()} 会失败。
     */
    private static final Map<String, Integer> RESPONSE_SCHEMA_STATUS = Map.ofEntries(
            Map.entry("BadRequestResponse", 400),
            Map.entry("UnauthorizedResponse", 401),
            Map.entry("ForbiddenResponse", 403),
            Map.entry("NotFoundResponse", 404),
            Map.entry("ConflictResponse", 409),
            Map.entry("GoneResponse", 410),
            Map.entry("PayloadTooLargeResponse", 413),
            Map.entry("UnsupportedMediaTypeResponse", 415),
            Map.entry("UnprocessableEntityResponse", 422),
            Map.entry("TooManyRequestsResponse", 429),
            Map.entry("InternalErrorResponse", 500),
            Map.entry("UnavailableResponse", 503));

    @Test
    void enumMatchesContractCodeSet() {
        Set<String> contract = new HashSet<>();
        for (Map.Entry<String, Integer> entry : RESPONSE_SCHEMA_STATUS.entrySet()) {
            contract.addAll(codesOf(entry.getKey()));
        }
        contract.add("OK"); // 成功响应的 code 字面量

        Set<String> declared = new LinkedHashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            declared.add(code.name());
        }

        assertThat(declared)
                .as("枚举必须与契约的 code 集合完全相同（多一个或少一个都是契约漂移）")
                .containsExactlyInAnyOrderElementsOf(contract);
    }

    @Test
    void everyContractErrorSchemaIsCovered() {
        JsonNode schemas = contract().get("components").get("schemas");
        Set<String> inContract = new LinkedHashSet<>();
        schemas.properties().forEach(field -> {
            String name = field.getKey();
            if (name.endsWith("Response") && field.getValue().get("properties").has("details")) {
                inContract.add(name);
            }
        });

        assertThat(RESPONSE_SCHEMA_STATUS.keySet())
                .as("契约新增了错误响应 schema；请在 RESPONSE_SCHEMA_STATUS 中登记它对应的 HTTP 状态")
                .containsExactlyInAnyOrderElementsOf(inContract);
    }

    @Test
    void singleStatusCodesMatchContractStatus() {
        Map<String, Set<Integer>> statuses = new HashMap<>();
        for (Map.Entry<String, Integer> entry : RESPONSE_SCHEMA_STATUS.entrySet()) {
            for (String code : codesOf(entry.getKey())) {
                statuses.computeIfAbsent(code, key -> new LinkedHashSet<>()).add(entry.getValue());
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : statuses.entrySet()) {
            if (entry.getValue().size() > 1) {
                // 契约中同一个 code 出现在多个状态下（当前只有 LICENSE_REVOKED：403 与 409），
                // 默认状态无法唯一确定，由抛出位置选用的异常类型决定。
                continue;
            }
            ErrorCode code = ErrorCode.valueOf(entry.getKey());
            assertThat(code.defaultStatus().value())
                    .as("%s 的默认 HTTP 状态应与契约一致", code.name())
                    .isEqualTo(entry.getValue().iterator().next());
        }
    }

    private static Set<String> codesOf(String schemaName) {
        JsonNode enumNode = contract()
                .get("components").get("schemas").get(schemaName)
                .get("properties").get("code").get("enum");
        Set<String> codes = new LinkedHashSet<>();
        enumNode.forEach(node -> codes.add(node.asString()));
        return codes;
    }

    private static JsonNode contract() {
        try (InputStream in = ApiCodeTests.class.getResourceAsStream(CONTRACT)) {
            assertThat(in).as("契约文件必须存在于 %s", CONTRACT).isNotNull();
            return new JsonMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
