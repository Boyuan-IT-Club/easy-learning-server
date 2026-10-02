package com.earlylearning.early_learning_server.ai.service.scoring;

import com.earlylearning.early_learning_server.ai.model.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringCommand;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;

/**
 * 单题评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；版本语义由 {@link RubricService} 共用。
 * 这里只剩单题评分特有的部分。请求形状的校验在 controller 层完成，这里收到的是
 * {@link AnswerScoringCommand}；返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p><b>一次提交只评一次作答</b>：{@code attempt} 标明这次是提示前还是提示后。
 * 服务端不把两次作答关联起来，也不计算"最终分"——云端只负责评分与校验，
 * 结果由客户端写入本地；「最终分取提示后分、未提示沿用提示前分」是客户端的合成规则。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
public interface AiAnswerScoringService {

    AiTask submit(AnswerScoringCommand command, int retryAttempt);
}
