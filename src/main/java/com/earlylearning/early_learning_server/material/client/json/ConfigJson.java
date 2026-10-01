package com.earlylearning.early_learning_server.material.client.json;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 包内 config.json 的解析。重复键直接判非法；整数位置出现字符串按错误处理。
 * 只负责 JSON 层面的合法性，业务语义由发布校验器承担。
 */
public final class ConfigJson {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(LogicalType.Integer,
                    config -> config.setCoercion(CoercionInputShape.String, CoercionAction.Fail))
            .build();

    private ConfigJson() {
    }

    public static JsonNode parse(byte[] content) {
        try {
            return JSON.readTree(content);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "config.json 不是合法的 JSON");
        }
    }
}
