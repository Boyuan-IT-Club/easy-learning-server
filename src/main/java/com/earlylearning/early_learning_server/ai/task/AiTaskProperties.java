package com.earlylearning.early_learning_server.ai.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 任务执行器的时间参数。
 *
 * <p>这两个值**一写错就静默废掉整条链路**，所以用启动期校验挡住：
 * <ul>
 *   <li>{@code timeout-seconds=0} 会让每个任务立刻超时——实测连提 4 个任务全部
 *       {@code FAILED / MODEL_TIMEOUT}；</li>
 *   <li>{@code result-ttl-seconds=0} 会让结果一出生就过期，客户端永远读不到。</li>
 * </ul>
 * 与其运行期大面积失败，不如启动就失败。
 *
 * @param resultTtlSeconds 成功后结果可读的时长
 * @param timeoutSeconds   单个任务的处理时长上限
 */
@Validated
@ConfigurationProperties(prefix = "ai.task")
public record AiTaskProperties(
        @DefaultValue("1800") @Min(1) @Max(86400) long resultTtlSeconds,
        @DefaultValue("600") @Min(1) @Max(86400) long timeoutSeconds) {
}
