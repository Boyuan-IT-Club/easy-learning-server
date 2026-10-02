package com.earlylearning.early_learning_server.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.earlylearning.early_learning_server.ai.client.ecnu.EcnuChatModel;
import com.earlylearning.early_learning_server.ai.client.scoring.ChatModelAnswerScorer;
import com.earlylearning.early_learning_server.ai.client.scoring.ChatModelStoryScorer;
import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ai.llm.provider=fake}（也是不配时的缺省）时的接线：应当是假实现。
 *
 * <p>把"没接真实模型"变成有测试记录的行为，而不是一个悄悄发生、事后靠人猜的事实——
 * 实际就是这么被误判成"适配器没做好"的。
 *
 * <p>顺带记一个坑：{@code ai.llm.provider=} 空值时，{@code havingValue="fake"} 与 {@code matchIfMissing}
 * 都不成立，假实现和真实实现都不会装配，上下文直接起不来（表现为启动失败而不是静默用假的）。
 * 所以运维侧要么不设这个变量，要么给一个明确的值。
 */
@SpringBootTest(properties = "ai.llm.provider=fake")
class DefaultProviderWiringTests {

    @Autowired
    private AnswerScorer answerScorer;

    @Autowired
    private StoryScorer storyScorer;

    @Autowired
    private ChatModel chatModel;

    @Test
    void withoutTheRealProviderTheFakesAreWired() {
        assertThat(answerScorer).isNotInstanceOf(ChatModelAnswerScorer.class);
        assertThat(storyScorer).isNotInstanceOf(ChatModelStoryScorer.class);
        assertThat(chatModel).isNotInstanceOf(EcnuChatModel.class);
    }
}
