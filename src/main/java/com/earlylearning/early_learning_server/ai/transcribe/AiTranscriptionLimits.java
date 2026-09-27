package com.earlylearning.early_learning_server.ai.transcribe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 转写接口的音频上限。
 *
 * @param maxSizeBytes   单段录音的字节上限
 * @param maxDurationMs  单段录音的时长上限；契约规定故事录音最多 10 分钟
 */
@Validated
@ConfigurationProperties(prefix = "ai.transcribe")
public record AiTranscriptionLimits(
        @DefaultValue("52428800") @Min(1) @Max(1073741824) long maxSizeBytes,
        @DefaultValue("600000") @Min(1000) @Max(600000) long maxDurationMs) {
}
