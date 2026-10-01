package com.earlylearning.early_learning_server.ai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.ai.controller.validation.StoryScoringRequestValidator;
import com.earlylearning.early_learning_server.ai.dto.StoryScoringRequest;
import com.earlylearning.early_learning_server.ai.dto.TaskHandleResponse;
import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringGroup;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringCommand;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.service.scoring.AiStoryScoringService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

/**
 * 故事叙述评分提交。
 *
 * <p>HTTP 是这一层的事：请求形状校验、wire → 领域命令的映射、状态码与任务凭据的转换。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@RestController
@RequestMapping("/api/ai")
public class AiStoryScoringController {

    private final AiStoryScoringService aiStoryScoringService;
    private final StoryScoringRequestValidator storyScoringRequestValidator;

    public AiStoryScoringController(AiStoryScoringService aiStoryScoringService,
                                    StoryScoringRequestValidator storyScoringRequestValidator) {
        this.aiStoryScoringService = aiStoryScoringService;
        this.storyScoringRequestValidator = storyScoringRequestValidator;
    }

    @PostMapping("/score")
    public ResponseEntity<ApiResponse<TaskHandleResponse>> score(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestBody StoryScoringRequest request) {
        storyScoringRequestValidator.validate(request);
        AiTask task = aiStoryScoringService.submit(toCommand(request), retryAttempt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(TaskHandleResponse.from(task)));
    }

    private StoryScoringCommand toCommand(StoryScoringRequest request) {
        List<ScoringGroup> groups = request.contentItems() == null ? null
                : request.contentItems().stream()
                        .map(item -> new ScoringGroup(item.contentItemId(), item.imageFileCodes(), item.rubricItemCode()))
                        .toList();
        List<ImageRef> images = request.images() == null ? null
                : request.images().stream()
                        .map(image -> new ImageRef(image.kind(), image.fileCode(), image.mimeType(),
                                image.contentBase64(), image.description(), image.confirmed()))
                        .toList();
        return new StoryScoringCommand(request.requestId(), request.inputRevision(), request.businessType(),
                request.activityId(), request.rubricVersion(), request.confirmedText(), request.storyContext(),
                groups, images);
    }
}
