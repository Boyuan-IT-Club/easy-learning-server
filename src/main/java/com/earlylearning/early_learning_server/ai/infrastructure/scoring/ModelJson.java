package com.earlylearning.early_learning_server.ai.infrastructure.scoring;

import com.earlylearning.early_learning_server.ai.domain.scoring.InvalidModelOutputException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模型输出解析的共用接缝：两个评分适配器（故事/单题）用同一套严格规则读模型回的 JSON。
 *
 * <ul>
 *   <li><b>严格重复键检测</b>：模型回"以条目号为键的对象"时，同一个键写两次宁可判失败，
 *       也不静默留最后一个（等于悄悄丢一次判断）；</li>
 *   <li><b>字符串 → 整数不自动转</b>：模型把分数写成 {@code "2"} 判失败——端点并不严格保证
 *       schema，这一层是必要的。</li>
 * </ul>
 *
 * <p>解析失败统一抛 {@link InvalidModelOutputException}，由任务执行层落成
 * {@code MODEL_OUTPUT_INVALID}——不合格输出不能变成"看起来成功"的分数。
 */
final class ModelJson {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(tools.jackson.databind.type.LogicalType.Integer,
                    config -> config.setCoercion(tools.jackson.databind.cfg.CoercionInputShape.String,
                            tools.jackson.databind.cfg.CoercionAction.Fail))
            .build();

    private ModelJson() {
    }

    static <T> T parse(String content, Class<T> type) {
        try {
            T output = JSON.readValue(content, type);
            if (output == null) {
                throw new IllegalArgumentException("内容为空");
            }
            return output;
        } catch (RuntimeException ex) {   // Jackson 3 的异常都是非受检的
            throw new InvalidModelOutputException("模型输出无法解析为评分结果：" + ex.getClass().getSimpleName());
        }
    }
}
