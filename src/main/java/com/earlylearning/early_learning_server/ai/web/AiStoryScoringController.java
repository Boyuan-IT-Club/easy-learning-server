package com.earlylearning.early_learning_server.ai.web;

import com.earlylearning.early_learning_server.ai.score.AiStoryScoringService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故事叙述评分提交。
 *
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
 */
@RestController
public class AiStoryScoringController {

    private final AiStoryScoringService scoringService;

    public AiStoryScoringController(AiStoryScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/api/ai/score")
    public ResponseEntity<ApiResponse<TaskHandleResponse>> score(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestBody StoryScoringRequest request) {
        TaskHandleResponse handle = scoringService.submit(request, retryAttempt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(handle));
    }
}
