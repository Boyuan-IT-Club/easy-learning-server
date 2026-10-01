package com.earlylearning.early_learning_server.ai.service.task;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;

import java.time.Instant;

import java.util.UUID;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * AI 任务的登记与查询。
 *
 * <p>权限：只能查询当前教师自己的任务，他人任务按 404 处理；本模块不校验，没有调用方身份可用。
 */
@Service
public class AiTaskService {

    private final AiTaskStore store;

    public AiTaskService(AiTaskStore store) {
        this.store = store;
    }

    /**
     * 查询任务。
     *
     * <p>返回领域对象而不是响应模型：转成 HTTP 形状是 web 层的事，
     * 否则 task 包就要反向依赖 web 包。
     *
     * <p>任务失败不是查询失败——失败信息在任务自身；只有任务不存在
     * （含已被清理、以及本进程重启前的任务）才抛 404。
     */
    public AiTask query(String taskId) {
        // 契约把 task_id 声明为 Uuid：格式不对属于请求不合法（400），
        // 而不是"任务不存在"——否则客户端无法区分"我传错了"与"任务真没了"。
        try {
            UUID.fromString(taskId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/task_id"));
        }
        AiTask task = store.find(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        task.expireIfNeeded(Instant.now());
        return task;
    }

    /** 登记新任务，初始阶段为 QUEUED。 */
    public AiTask register(AiTask task) {
        store.save(task);
        return task;
    }
}
