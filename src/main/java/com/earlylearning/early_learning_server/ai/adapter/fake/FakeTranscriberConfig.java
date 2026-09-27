package com.earlylearning.early_learning_server.ai.adapter.fake;

import com.earlylearning.early_learning_server.ai.task.AiTaskFailedException;
import com.earlylearning.early_learning_server.ai.port.SpeechTranscriber;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 语音识别的假实现：接入真实服务前用它把链路跑通。
 *
 * <p>行为可控（通过系统属性触发），供联调与测试使用：
 * <ul>
 *   <li>{@code ai.fake-transcriber.fail=true} → 抛出可重试的识别失败</li>
 *   <li>{@code ai.fake-transcriber.fail-permanently=true} → 抛出不可重试的识别失败</li>
 *   <li>{@code ai.fake-transcriber.empty=true} → 返回空文本（"没有识别到内容"，不是失败）</li>
 * </ul>
 *
 * <p>接入真实实现时，提供自己的 {@link SpeechTranscriber} Bean 并标注 {@code @Primary} 即可覆盖。
 */
@Configuration(proxyBeanMethods = false)
public class FakeTranscriberConfig {

    private static final Logger log = LoggerFactory.getLogger(FakeTranscriberConfig.class);

    private static final String DEFAULT_TRANSCRIPT = "（示例识别结果）";

    @Bean
    public SpeechTranscriber fakeSpeechTranscriber() {
        return (audio, mimeType) -> {
            if (Boolean.getBoolean("ai.fake-transcriber.fail-permanently")) {
                throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "示例：录音无法识别", false, null);
            }
            if (Boolean.getBoolean("ai.fake-transcriber.fail")) {
                throw new AiTaskFailedException(TaskFailureCode.ASR_FAILED, "示例：识别服务暂时不可用", true, null);
            }
            if (Boolean.getBoolean("ai.fake-transcriber.empty")) {
                return "";
            }
            log.info("假识别器被调用 mimeType={} audioBytes={}", mimeType, audio.length);
            return DEFAULT_TRANSCRIPT;
        };
    }
}
