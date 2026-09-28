package com.earlylearning.early_learning_server.ai.adapter.ecnu;

import com.earlylearning.early_learning_server.ai.llm.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * provider=ecnu 时装配 ECNU 适配器。
 *
 * <p>这里做一次**启动期检查**：选了 ecnu 却没配令牌，就直接启动失败——
 * 否则要等到第一次评分才暴露，而且暴露成任务失败，排查成本高得多。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "ecnu")
public class EcnuChatModelConfig {

    @Bean
    public ChatModel ecnuChatModel(EcnuProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "ai.llm.provider=ecnu 但 ai.llm.ecnu.api-key 为空；请在 .env 里填 AI_LLM_ECNU_API_KEY，"
                            + "或把 AI_LLM_PROVIDER 改回 fake");
        }
        return new EcnuChatModel(properties);
    }
}
