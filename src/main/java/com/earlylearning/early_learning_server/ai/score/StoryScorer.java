package com.earlylearning.early_learning_server.ai.score;

import com.earlylearning.early_learning_server.ai.web.StoryScoringRequest;

/**
 * 故事评分适配器。
 *
 * <p>实现可替换：接口只声明契约，不关心谁来实现。
 * 入参直接复用请求模型——它是扁平的数据载体，再包一层领域对象只会多一份需要同步的结构。
 *
 * <p>返回的 {@link AiScore} **会经过 {@code ScoreValidator} 的运行时语义校验**，
 * 不合格会被落成 {@code MODEL_OUTPUT_INVALID} 任务失败，而不是返回一个看起来成功的分数。
 *
 * <p><b>默认实现见 {@link com.earlylearning.early_learning_server.ai.adapter.fake.FakeStoryScorerConfig}（假实现，接通真实评分模型前把链路跑通用）。</b>
 * 接入真实评分模型时：提供本接口同类型的 Bean 并标 {@code @Primary} 即可覆盖，业务代码不用改。
 */
public interface StoryScorer {

    /**
     * 按给定版本评分。
     *
     * @param rubricVersion 实际采用的评分标准版本；由服务端决定，不由请求决定
     * @throws AiTaskFailedException 调用模型失败
     */
    AiScore score(StoryScoringRequest request, String rubricVersion);
}
