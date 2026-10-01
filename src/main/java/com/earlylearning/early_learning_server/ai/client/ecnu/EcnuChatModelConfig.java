package com.earlylearning.early_learning_server.ai.client.ecnu;

import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * provider=ecnu 时装配 ECNU 适配器。
 *
 * <p>这里做一次启动期检查：选了 ecnu 却没配令牌，就直接启动失败——
 * 否则要等到第一次评分才暴露，而且暴露成任务失败，排查成本高得多。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "ecnu")
public class EcnuChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(EcnuChatModelConfig.class);


    @Bean
    public ChatModel ecnuChatModel(EcnuProperties properties) {
        log.info("AI 评分使用 ECNU 真实模型：baseUrl={} 文本模型={} 多模态模型={}",
                properties.baseUrl(), properties.modelText(), properties.modelVision());
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "ai.llm.provider=ecnu 但 ai.llm.ecnu.api-key 为空；请在 .env 里填 AI_LLM_ECNU_API_KEY，"
                            + "或把 AI_LLM_PROVIDER 改回 fake");
        }
        return new EcnuChatModel(properties);
    }
}
