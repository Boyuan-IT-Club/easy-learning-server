package com.earlylearning.early_learning_server.ai.model.rubric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 评分规则的固定配置。
 *
 * @param version 当前实际采用的评分标准版本；它长期固定，不随新故事变化
 */
@Validated
@ConfigurationProperties(prefix = "ai.rubric")
public record RubricProperties(@DefaultValue("narrative-assessment-v2") @NotBlank @Size(max = 128) String version) {
}
