package com.earlylearning.early_learning_server.ai.client.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.earlylearning.early_learning_server.ai.client.fake.FakeAnswerScorerConfig;
import com.earlylearning.early_learning_server.ai.client.rubric.RubricConfigLoader;
import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScorer;

/**
 * provider=ecnu 时用真实评分实现（它只依赖 {@link ChatModel} 端口，所以接入任何厂商都同一份代码）。
 *
 * <p>假实现见 {@code ai.adapter.fake.FakeAnswerScorerConfig}，它挂在 provider=fake 下，
 * 两者互斥。将来接第二家厂商，加一对同形状的 Bean 即可，评分逻辑不用改。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "ecnu")
public class ChatModelAnswerScorerConfig {

    @Bean
    public AnswerScorer chatModelAnswerScorer(ChatModel chatModel, RubricConfigLoader rubricConfig) {
        return new ChatModelAnswerScorer(chatModel, rubricConfig.config());
    }
}
