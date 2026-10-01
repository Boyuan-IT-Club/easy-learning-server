package com.earlylearning.early_learning_server.ai.controller;
import com.earlylearning.early_learning_server.ai.controller.validation.AnswerScoringRequestValidator;
import com.earlylearning.early_learning_server.ai.dto.AnswerScoringRequest;
import com.earlylearning.early_learning_server.ai.dto.TaskHandleResponse;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoredQuestion;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringCommand;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.service.scoring.AiAnswerScoringService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单题（提示前／提示后）评分提交。
 *
 * <p>HTTP 是这一层的事：请求形状校验、wire → 领域命令的映射、状态码与任务凭据的转换。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@RestController
public class AiAnswerScoringController {

    private final AiAnswerScoringService scoringService;
    private final AnswerScoringRequestValidator requestValidator;

    public AiAnswerScoringController(AiAnswerScoringService scoringService,
                                     AnswerScoringRequestValidator requestValidator) {
        this.scoringService = scoringService;
        this.requestValidator = requestValidator;
    }

    @PostMapping("/api/ai/score-answer")
    public ResponseEntity<ApiResponse<TaskHandleResponse>> scoreAnswer(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestBody AnswerScoringRequest request) {
        requestValidator.validate(request);
        AiTask task = scoringService.submit(toCommand(request), retryAttempt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(TaskHandleResponse.from(task)));
    }

    private AnswerScoringCommand toCommand(AnswerScoringRequest request) {
        List<ImageRef> images = request.images() == null ? null
                : request.images().stream()
                        .map(image -> new ImageRef(image.kind(), image.fileCode(), image.mimeType(),
                                image.contentBase64(), image.description(), image.confirmed()))
                        .toList();
        ScoredQuestion question = request.question() == null ? null
                : new ScoredQuestion(request.question().questionId(), request.question().text(), request.question().hint());
        return new AnswerScoringCommand(request.requestId(), request.inputRevision(), request.businessType(),
                request.activityId(), request.rubricVersion(), request.confirmedText(), request.storyContext(),
                question, request.attempt(), images);
    }
}
