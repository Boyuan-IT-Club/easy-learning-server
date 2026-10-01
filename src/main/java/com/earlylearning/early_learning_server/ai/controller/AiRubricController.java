package com.earlylearning.early_learning_server.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.ai.dto.RubricCatalogResponse;
import com.earlylearning.early_learning_server.ai.service.rubric.RubricCatalogService;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 评分条目目录查询。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRubricController {

    private final RubricCatalogService rubricCatalogService;

    @GetMapping("/rubrics")
    public ApiResponse<RubricCatalogResponse> catalog() {
        return ApiResponse.ok(RubricCatalogResponse.from(rubricCatalogService.catalog()));
    }
}
