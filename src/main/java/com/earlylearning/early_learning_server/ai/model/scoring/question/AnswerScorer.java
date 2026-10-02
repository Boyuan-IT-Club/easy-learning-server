package com.earlylearning.early_learning_server.ai.model.scoring.question;

import com.earlylearning.early_learning_server.ai.model.task.AiTaskFailedException;

/**
 * 单题评分适配器。
 *
 * <p>实现可替换：接口只声明契约，不关心谁来实现。
 * 入参是领域对象（{@link AnswerScoringInput}）：图片已解析成字节或确认说明，
 * 适配器不必知道 {@code file_code} 怎么变成内容，也不碰存储与 HTTP 模型。
 *
 * <p>返回的分数会经过 {@code QuestionScoreValidator} 的运行时语义校验，
 * 不合格会被落成 {@code MODEL_OUTPUT_INVALID} 任务失败，而不是返回一个看起来成功的分数。
 *
 * <p><b>默认实现见 {@code infrastructure.fake.FakeAnswerScorerConfig}（假实现，接通真实评分模型前把链路跑通用）。</b>
 * 接入真实评分模型时：提供本接口同类型的 Bean 并标 {@code @Primary} 即可覆盖，业务代码不用改。
 */
public interface AnswerScorer {

    /**
     * 评一次作答。
     *
     * @param rubricVersion 实际采用的评分标准版本；由服务端决定，不由请求决定
     * @throws AiTaskFailedException 调用模型失败
     */
    AnswerScoringOutput score(AnswerScoringInput input, String rubricVersion);
}
