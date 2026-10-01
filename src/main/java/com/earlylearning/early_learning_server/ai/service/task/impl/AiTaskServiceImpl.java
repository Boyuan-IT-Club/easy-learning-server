package com.earlylearning.early_learning_server.ai.service.task.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskStore;
import com.earlylearning.early_learning_server.ai.service.task.AiTaskService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/** {@link AiTaskService} 的实现。 */
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskStore store;

    public AiTaskServiceImpl(AiTaskStore store) {
        this.store = store;
    }

    @Override
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

    @Override
    public AiTask register(AiTask task) {
        store.save(task);
        return task;
    }
}
