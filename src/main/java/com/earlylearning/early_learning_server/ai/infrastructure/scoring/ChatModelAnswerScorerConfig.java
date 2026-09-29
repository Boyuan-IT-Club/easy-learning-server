package com.earlylearning.early_learning_server.ai.infrastructure.scoring;
import com.earlylearning.early_learning_server.ai.infrastructure.fake.FakeAnswerScorerConfig;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScorer;

import com.earlylearning.early_learning_server.ai.domain.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.infrastructure.rubric.RubricConfigLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
