package com.earlylearning.early_learning_server.material.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.material.dto.AssessmentMaterialDownloadResponse;
import com.earlylearning.early_learning_server.material.dto.AssessmentMaterialVersionsResponse;
import com.earlylearning.early_learning_server.material.model.MaterialVersionSummary;
import com.earlylearning.early_learning_server.material.service.AssessmentMaterialQueryService;

import tools.jackson.databind.ObjectMapper;

/**
 * 评估材料的平板端入口：版本目录与指定版本的依赖下载。
 *
 * <p>权限：管理员或教师凭证；本模块不校验。版本目录供客户端比对本地版本，
 * 下载清单经教师确认后由客户端异步拉取文件。
 */
@RestController
public class AssessmentMaterialCatalogController {

    private final AssessmentMaterialQueryService queryService;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialCatalogController(AssessmentMaterialQueryService queryService,
                                               ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/assessment-materials/versions")
    public ApiResponse<AssessmentMaterialVersionsResponse> versions() {
        List<MaterialVersionSummary> summaries = queryService.versions();
        return ApiResponse.ok(AssessmentMaterialVersionsResponse.from(summaries));
    }

    @GetMapping("/api/assessment-materials/{code}/{version}")
    public ApiResponse<AssessmentMaterialDownloadResponse> download(
            @PathVariable String code,
            @PathVariable String version) {
        return ApiResponse.ok(
                AssessmentMaterialDownloadResponse.from(queryService.download(code, version), objectMapper));
    }
}
