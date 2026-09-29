package com.earlylearning.early_learning_server.ai.domain.scoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 评分请求的输入上限。
 *
 * <p>契约在 {@code details.limit.name} 里列了 {@code text_length} 与 {@code image_count}，
 * 说明这两项预期有部署上限；此前只在枚举里存在、没有任何地方执行——
 * 实测 30 万字的确认文本被直接受理并评分成功。
 *
 * <p>{@code text_length} 注明的是 UTF-16 code unit（即 Java 的 {@code String.length()}），
 * 不是字符数：代理对算两个。
 *
 * @param maxTextLength 单段确认文本与故事依据的 UTF-16 code unit 上限
 * @param maxImageCount 单次请求的图片数量上限
 * @param maxImageBytes 单张图片字节上限（ECNU 文档未给图片上限，这里由业务侧定）
 */
@Validated
@ConfigurationProperties(prefix = "ai.scoring")
public record ScoringLimits(
        @DefaultValue("20000") @Min(1) @Max(1000000) int maxTextLength,
        @DefaultValue("20") @Min(1) @Max(1000) int maxImageCount,
        @DefaultValue("5242880") @Min(1024) @Max(52428800) long maxImageBytes) {
}
