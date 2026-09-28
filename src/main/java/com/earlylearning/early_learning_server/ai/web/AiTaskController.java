package com.earlylearning.early_learning_server.ai.web;

import com.earlylearning.early_learning_server.ai.task.AiTaskService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 任务查询。
 *
 * <p>权限：契约要求只能查询当前教师自己的任务；本模块不校验。
 */
@RestController
public class AiTaskController {

    private final AiTaskService aiTaskService;

    public AiTaskController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @GetMapping("/api/ai/tasks/{task_id}")
    public ApiResponse<AiTaskResponse> get(@PathVariable("task_id") String taskId) {
        return ApiResponse.ok(AiTaskResponse.from(aiTaskService.query(taskId)));
    }
}
