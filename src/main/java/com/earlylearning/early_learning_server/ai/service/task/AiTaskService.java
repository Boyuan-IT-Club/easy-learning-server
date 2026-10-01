package com.earlylearning.early_learning_server.ai.service.task;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;

/**
 * AI 任务的登记与查询。
 *
 * <p>权限：只能查询当前教师自己的任务，他人任务按 404 处理；本模块不校验，没有调用方身份可用。
 */
public interface AiTaskService {

    /**
     * 查询任务。
     *
     * <p>返回领域对象而不是响应模型：转成 HTTP 形状是 web 层的事，
     * 否则 task 包就要反向依赖 web 包。
     *
     * <p>任务失败不是查询失败——失败信息在任务自身；只有任务不存在
     * （含已被清理、以及本进程重启前的任务）才抛 404。
     */
    AiTask query(String taskId);

    /** 登记新任务，初始阶段为 QUEUED。 */
    AiTask register(AiTask task);
}
