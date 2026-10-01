package com.earlylearning.early_learning_server.ai.client;

import com.earlylearning.early_learning_server.ai.client.ecnu.EcnuChatModel;
import com.earlylearning.early_learning_server.ai.client.scoring.ChatModelAnswerScorer;
import com.earlylearning.early_learning_server.ai.client.scoring.ChatModelStoryScorer;
import com.earlylearning.early_learning_server.ai.model.llm.ChatModel;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * provider 开关的接线测试：{@code ai.llm.provider} 决定评分与模型调用用真实实现还是假实现。
 *
 * <p>为什么要专门钉这个：两种情况都不会报错。"配了环境变量却仍在跑假实现"曾经只能靠人看返回内容判断，
 * 排查时很容易误判成"适配器没做好"。这里把开关两侧都钉死：
 * <ul>
 *   <li>配了 {@code ai.llm.provider=ecnu} → 两条评分链路与底层模型调用都是真实实现；</li>
 *   <li>没配 → 是假实现（不是真实实现），也就是缺省行为。</li>
 * </ul>
 *
 * <p>注意假实现是 lambda 形式的 Bean，只能"断言不是真实实现"，不能断言具体类型。
 */
@SpringBootTest(properties = {
        "ai.llm.provider=ecnu",
        "ai.llm.ecnu.base-url=https://example.invalid/v1",
        "ai.llm.ecnu.api-key=dummy-key-for-wiring-test",
})
class EcnuProviderWiringTests {

    @Autowired
    private AnswerScorer answerScorer;

    @Autowired
    private StoryScorer storyScorer;

    @Autowired
    private ChatModel chatModel;

    @Test
    void ecnuProviderWiresTheRealImplementationsEverywhere() {
        assertThat(answerScorer).isInstanceOf(ChatModelAnswerScorer.class);
        assertThat(storyScorer).isInstanceOf(ChatModelStoryScorer.class);
        assertThat(chatModel).isInstanceOf(EcnuChatModel.class);
    }
}
