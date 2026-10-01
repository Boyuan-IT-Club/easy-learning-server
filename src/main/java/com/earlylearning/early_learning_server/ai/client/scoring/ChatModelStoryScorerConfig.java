package com.earlylearning.early_learning_server.ai.client.scoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.earlylearning.early_learning_server.ai.client.fake.FakeStoryScorerConfig;
import com.earlylearning.early_learning_server.ai.client.rubric.RubricConfigLoader;
import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;

/**
 * provider=ecnu 时用真实故事评分实现（它只依赖 {@link ChatModel} 端口，所以接入任何厂商都同一份代码）。
 *
 * <p>假实现见 {@code ai.adapter.fake.FakeStoryScorerConfig}，它挂在 provider=fake 下，两者互斥。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "ecnu")
public class ChatModelStoryScorerConfig {

    @Bean
    public StoryScorer chatModelStoryScorer(ChatModel chatModel, RubricConfigLoader rubricConfig) {
        return new ChatModelStoryScorer(chatModel, rubricConfig.config());
    }
}
