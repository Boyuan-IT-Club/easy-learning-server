package com.earlylearning.early_learning_server.ai.client.ecnu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * ECNU（华东师范大学 ChatECNU）接入配置。
 *
 * <p>平台文档：https://developer.ecnu.edu.cn/vitepress/llm/
 *
 * <p>两个模型分开配，是因为能力不同：{@code ecnu-max} 上下文更大、不支持图片理解；
 * {@code ecnu-plus} 支持图片理解。带图必须用后者，所以由程序按请求里有没有图片自动选，
 * 不交给调用方决定。
 *
 * @param baseUrl         OpenAI 兼容的 base url
 * @param apiKey          令牌；provider=ecnu 时必填（留空会在启动时报错，见 {@link EcnuChatModelConfig}）
 * @param modelText       不带图片时的模型
 * @param modelVision     带图片时的模型
 * @param thinking        是否开启思考模式
 * @param reasoningEffort 思考强度；留空表示不传（各模型可选档位不同）
 * @param timeoutSeconds  单次调用超时
 * @param maxImageBytes   单张图片字节上限（平台文档未给，这里自定）
 */
@Validated
@ConfigurationProperties(prefix = "ai.llm.ecnu")
public record EcnuProperties(
        @DefaultValue("https://chat.ecnu.edu.cn/open/api/v1") @NotBlank String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("ecnu-max") @NotBlank String modelText,
        @DefaultValue("ecnu-plus") @NotBlank String modelVision,
        @DefaultValue("false") boolean thinking,
        @DefaultValue("") String reasoningEffort,
        @DefaultValue("120") @Min(1) @Max(600) long timeoutSeconds,
        @DefaultValue("5242880") @Min(1024) @Max(52428800) long maxImageBytes) {
}
