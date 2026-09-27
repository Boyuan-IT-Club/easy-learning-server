package com.earlylearning.early_learning_server.ai.web;

import com.earlylearning.early_learning_server.ai.rubric.RubricCatalogService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评分条目目录查询。
 *
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
 */
@RestController
public class AiRubricController {

    private final RubricCatalogService catalogService;

    public AiRubricController(RubricCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/ai/rubrics")
    public ApiResponse<RubricCatalogResponse> catalog() {
        return ApiResponse.ok(catalogService.catalog());
    }
}
