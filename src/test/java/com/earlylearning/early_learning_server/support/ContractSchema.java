package com.earlylearning.early_learning_server.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 按仓库里的契约副本校验响应体：找到 operationId 与状态码对应的 schema，逐层比对。
 *
 * <p>只实现契约实际用到的关键字（$ref、type、nullable、enum、properties、required、
 * additionalProperties:false、items、长度/数量/数值范围、pattern、date-time、allOf/oneOf、minProperties）。
 * 守的是 {@code additionalProperties: false}：多返回一个字段、少一个必填字段、类型写错都会在这里暴露。
 */
public final class ContractSchema {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode CONTRACT = load();

    private ContractSchema() {
    }

    /** @return 违反之处；为空表示符合 */
    public static List<String> violations(String operationId, int status, String body) {
        JsonNode schema = responseSchema(operationId, status);
        List<String> errors = new ArrayList<>();
        check(schema, JSON.readTree(body), "", errors);
        return errors;
    }

    private static JsonNode responseSchema(String operationId, int status) {
        for (var path : CONTRACT.get("paths").properties()) {
            for (var op : path.getValue().properties()) {
                if (operationId.equals(op.getValue().path("operationId").asString())) {
                    JsonNode response = op.getValue().path("responses").path(Integer.toString(status));
                    if (response.isMissingNode()) {
                        throw new AssertionError(operationId + " 未声明 " + status + " 响应");
                    }
                    response = resolve(response);
                    return response.path("content").path("application/json").path("schema");
                }
            }
        }
        throw new AssertionError("契约里没有 operationId=" + operationId);
    }

    private static void check(JsonNode schema, JsonNode value, String at, List<String> errors) {
        schema = resolve(schema);
        if (value.isNull()) {
            if (!schema.path("nullable").asBoolean(false)) {
                errors.add(at + ": 不允许 null");
            }
            return;
        }
        if (schema.has("allOf")) {
            for (JsonNode part : schema.get("allOf")) {
                check(part, value, at, errors);
            }
        }
        if (schema.has("oneOf")) {
            int matched = 0;
            for (JsonNode option : schema.get("oneOf")) {
                List<String> optionErrors = new ArrayList<>();
                check(option, value, at, optionErrors);
                if (optionErrors.isEmpty()) {
                    matched++;
                }
            }
            if (matched != 1) {
                errors.add(at + ": oneOf 命中 " + matched + " 个");
            }
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                found |= allowed.equals(value);
            }
            if (!found) {
                errors.add(at + ": " + value + " 不在枚举 " + schema.get("enum") + " 中");
            }
        }
        String type = schema.path("type").asString(null);
        if (type == null) {
            return;
        }
        switch (type) {
            case "object" -> checkObject(schema, value, at, errors);
            case "array" -> checkArray(schema, value, at, errors);
            case "string" -> checkString(schema, value, at, errors);
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    errors.add(at + ": 应为整数，实际 " + value);
                } else {
                    checkRange(schema, value, at, errors);
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    errors.add(at + ": 应为数字，实际 " + value);
                } else {
                    checkRange(schema, value, at, errors);
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    errors.add(at + ": 应为布尔，实际 " + value);
                }
            }
            default -> errors.add(at + ": 未支持的类型 " + type);
        }
    }

    private static void checkObject(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (!value.isObject()) {
            errors.add(at + ": 应为对象，实际 " + value);
            return;
        }
        JsonNode properties = schema.path("properties");
        for (JsonNode required : schema.path("required")) {
            if (!value.has(required.asString())) {
                errors.add(at + "/" + required.asString() + ": 缺少必填字段");
            }
        }
        boolean closed = schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean(true);
        Set<String> seen = new HashSet<>();
        for (var field : value.properties()) {
            seen.add(field.getKey());
            JsonNode propertySchema = properties.path(field.getKey());
            if (propertySchema.isMissingNode()) {
                if (closed) {
                    errors.add(at + "/" + field.getKey() + ": 契约未声明该字段（additionalProperties: false）");
                }
                continue;
            }
            check(propertySchema, field.getValue(), at + "/" + field.getKey(), errors);
        }
        if (schema.has("minProperties") && seen.size() < schema.get("minProperties").asInt()) {
            errors.add(at + ": 字段数少于 minProperties");
        }
    }

    private static void checkArray(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (!value.isArray()) {
            errors.add(at + ": 应为数组，实际 " + value);
            return;
        }
        if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) {
            errors.add(at + ": 元素少于 minItems");
        }
        if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) {
            errors.add(at + ": 元素多于 maxItems");
        }
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> unique = new HashSet<>();
            value.forEach(unique::add);
            if (unique.size() != value.size()) {
                errors.add(at + ": 元素重复（uniqueItems）");
            }
        }
        for (int i = 0; i < value.size(); i++) {
            check(schema.path("items"), value.get(i), at + "/" + i, errors);
        }
    }

    private static void checkString(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (!value.isString()) {
            errors.add(at + ": 应为字符串，实际 " + value);
            return;
        }
        String text = value.asString();
        int length = text.codePointCount(0, text.length());
        if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
            errors.add(at + ": 短于 minLength");
        }
        if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
            errors.add(at + ": 长于 maxLength");
        }
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asString()).matcher(text).find()) {
            errors.add(at + ": 不匹配 pattern " + schema.get("pattern").asString());
        }
        if ("date-time".equals(schema.path("format").asString(null))) {
            try {
                Instant.parse(text);
            } catch (DateTimeParseException e) {
                errors.add(at + ": 不是 UTC ISO 8601 时间戳 " + text);
            }
        }
    }

    private static void checkRange(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (schema.has("minimum") && value.asDouble() < schema.get("minimum").asDouble()) {
            errors.add(at + ": 小于 minimum");
        }
        if (schema.has("maximum") && value.asDouble() > schema.get("maximum").asDouble()) {
            errors.add(at + ": 大于 maximum");
        }
    }

    private static JsonNode resolve(JsonNode node) {
        while (node.has("$ref")) {
            JsonNode target = CONTRACT;
            for (String part : node.get("$ref").asString().substring(2).split("/")) {
                target = target.path(part);
            }
            if (target.isMissingNode()) {
                throw new AssertionError("契约里找不到 " + node.get("$ref").asString());
            }
            node = target;
        }
        return node;
    }

    private static JsonNode load() {
        try (InputStream in = ContractSchema.class.getResourceAsStream("/contract/early-learning-api.openapi.json")) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
