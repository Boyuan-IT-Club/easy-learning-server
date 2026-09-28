package com.earlylearning.early_learning_server.ai.adapter.fake;

import java.util.concurrent.atomic.AtomicInteger;

import com.earlylearning.early_learning_server.ai.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.llm.ChatModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认（假）大模型实现：没有令牌也能把链路跑通，测试也不需要联网。
 *
 * <p>行为可控（系统属性）：
 * <ul>
 *   <li>{@code ai.fake-chat.fail=true} → 抛可重试的调用失败</li>
 *   <li>{@code ai.fake-chat.fail-permanently=true} → 抛不可重试的调用失败</li>
 *   <li>{@code ai.fake-chat.content=…} → 指定返回内容（默认是一份能过评分校验的最小 JSON）</li>
 *   <li>{@code ai.fake-chat.model=<名字>} → 报告用的模型名（默认按有无图片区分，模拟厂商能力差异）</li>
 * </ul>
 *
 * <p>接入真实厂商时把 {@code ai.llm.provider} 改成对应厂商即可，这个 Bean 自动让位。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "fake", matchIfMissing = true)
public class FakeChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(FakeChatModelConfig.class);

    /** 默认返回：能通过单题评分校验的最小 JSON（空证据是合法的）。 */
    private static final String DEFAULT_CONTENT =
            "{\"score\":1,\"max_score\":2,\"reason\":\"示例理由（假实现固定输出）\",\"evidence\":[]}";

    @Bean
    public ChatModel fakeChatModel() {
        AtomicInteger calls = new AtomicInteger();
        return new ChatModel() {
            @Override
            public String modelFor(ChatRequest request) {
                String override = System.getProperty("ai.fake-chat.model");
                if (override != null && !override.isBlank()) {
                    return override;
                }
                return request.hasImages() ? "fake-vision-model" : "fake-text-model";
            }

            @Override
            public ChatResponse complete(ChatRequest request) {
                if (Boolean.getBoolean("ai.fake-chat.fail-permanently")) {
                    throw new ChatModelException("示例：模型调用被拒绝", false);
                }
                if (Boolean.getBoolean("ai.fake-chat.fail")) {
                    throw new ChatModelException("示例：模型调用暂时不可用", true);
                }
                String content = System.getProperty("ai.fake-chat.content", DEFAULT_CONTENT);
                log.info("假大模型被调用 第{}次 带图={} 模型={}",
                        calls.incrementAndGet(), request.hasImages(), modelFor(request));
                return new ChatResponse(content, modelFor(request));
            }
        };
    }
}
