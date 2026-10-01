package com.earlylearning.early_learning_server.ai.service.scoring;

import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringCommand;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;

/**
 * 故事评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；这里负责评分特有的部分：评分依据版本的确定、
 * 图片解析，以及调用模型并对输出做运行时校验。请求形状的校验在 controller 层完成，这里收到的是
 * {@link StoryScoringCommand}；返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p>版本规则：
 * <ul>
 *   <li>首次可省略版本 → 用服务端固定配置，并在任务凭据里返回实际版本；</li>
 *   <li>指定版本当前不可用 → 503 {@code RUBRIC_UNAVAILABLE}，不静默换版；</li>
 *   <li>同一任务的重试继续使用任务记录的实际评分版本——所以重试时以任务记录的版本为准，
 *       请求若指定了别的版本则按"不可用"拒绝，而不是悄悄改用新版本。</li>
 * </ul>
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
public interface AiStoryScoringService {

    AiTask submit(StoryScoringCommand command, int retryAttempt);
}
