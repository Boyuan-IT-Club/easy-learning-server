package com.earlylearning.early_learning_server.material.controller;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.material.service.AssessmentMaterialPublishService;
import com.earlylearning.early_learning_server.material.service.AssessmentMaterialQueryService;
import com.earlylearning.early_learning_server.material.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.material.entity.ContentStatus;
import com.earlylearning.early_learning_server.material.model.MaterialPage;
import com.earlylearning.early_learning_server.material.dto.AssessmentMaterialPageResponse;
import com.earlylearning.early_learning_server.material.dto.AssessmentMaterialResponse;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/**
 * 评估材料的管理端入口：ZIP 发布、版本列表与禁用。
 *
 * <p>权限：管理员凭证；本模块不校验。file 是唯一表单字段，包内 config.json 携带其余发布信息。
 */
@RestController
@Validated
public class AssessmentMaterialAdminController {

    private final AssessmentMaterialPublishService publishService;
    private final AssessmentMaterialQueryService queryService;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialAdminController(AssessmentMaterialPublishService publishService,
                                             AssessmentMaterialQueryService queryService,
                                             ObjectMapper objectMapper) {
        this.publishService = publishService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/admin/assessment-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssessmentMaterialResponse>> publish(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestPart("file") MultipartFile file) {
        IncomingFile incoming = new IncomingFile(file.getSize(), file.getOriginalFilename(),
                file.getContentType(), file::transferTo);
        AssessmentMaterial saved = publishService.publish(idempotencyKey, incoming);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(AssessmentMaterialResponse.from(saved,
                        objectMapper.readTree(saved.getActivityConfigsJson()))));
    }

    @GetMapping("/admin/assessment-materials")
    public ApiResponse<AssessmentMaterialPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "official_material_code", required = false) String officialMaterialCode,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String keyword) {
        MaterialPage pageResult = queryService.list(page, pageSize, officialMaterialCode, status, keyword);
        return ApiResponse.ok(AssessmentMaterialPageResponse.from(pageResult));
    }

    @PostMapping("/admin/assessment-materials/{id}/disable")
    public ApiResponse<AssessmentMaterialResponse> disable(@PathVariable int id) {
        AssessmentMaterial disabled = queryService.disable(id);
        return ApiResponse.ok(AssessmentMaterialResponse.from(disabled,
                objectMapper.readTree(disabled.getActivityConfigsJson())));
    }
}
