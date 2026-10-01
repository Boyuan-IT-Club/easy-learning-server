package com.earlylearning.early_learning_server.storage.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.earlylearning.early_learning_server.common.enums.CloudFileKind;
import com.earlylearning.early_learning_server.common.enums.CloudFileStatus;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.dto.AdminFilePageResponse;
import com.earlylearning.early_learning_server.storage.dto.CloudFileResponse;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;
import com.earlylearning.early_learning_server.storage.service.CloudFileDeletionService;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.service.CloudFileService;

/**
 * 官方文件的管理端接口：上传、列表、标记删除。
 *
 * <p>HTTP 是这一层的事：multipart → 领域入参的适配、状态码与响应形状；幂等快照存的是领域对象，
 * 首次与重放两条路径在这里用同一个映射转成响应，因此表现一致。
 *
 * <p>权限：管理员凭证；本模块不校验。
 */
@RestController
@RequestMapping("/admin/files")
@Validated
public class AdminFileController {

    private final CloudFileService cloudFileService;
    private final CloudFileQueryService cloudFileQueryService;
    private final CloudFileDeletionService cloudFileDeletionService;

    public AdminFileController(CloudFileService cloudFileService,
                               CloudFileQueryService cloudFileQueryService,
                               CloudFileDeletionService cloudFileDeletionService) {
        this.cloudFileService = cloudFileService;
        this.cloudFileQueryService = cloudFileQueryService;
        this.cloudFileDeletionService = cloudFileDeletionService;
    }

    /**
     * 标记删除。
     *
     * <p>是标记不是物理删除：只把状态改成 {@code DELETED}，对象本身留在存储里
     * （契约：仅从未发布的孤儿对象可由后台按宽限期清理）。
     */
    @PostMapping("/{file_code}/delete")
    public ApiResponse<CloudFileResponse> markDeleted(@PathVariable("file_code") String fileCode) {
        return ApiResponse.ok(CloudFileResponse.from(cloudFileDeletionService.markDeleted(fileCode)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CloudFileResponse>> upload(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            // file_kind / file_name 是 multipart 的表单字段，不是文件部分：
            // 用 @RequestPart 会让 Spring 拿消息转换器去解枚举，客户端会收到 415。
            @RequestParam("file_kind") CloudFileKind fileKind,
            @RequestParam(value = "file_name", required = false) String fileName) {
        IncomingFile incoming = new IncomingFile(file.getSize(), file.getOriginalFilename(),
                file.getContentType(), file::transferTo);
        CloudFile saved = cloudFileService.upload(idempotencyKey, incoming, fileKind, fileName);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CloudFileResponse.from(saved)));
    }

    /**
     * 分页查询官方文件与引用数。
     *
     * <p>{@code file_code} 是精确匹配，契约用它替代单独的详情接口；{@code keyword} 对文件名与编号做包含匹配。
     */
    @GetMapping
    public ApiResponse<AdminFilePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "file_code", required = false) String fileCode,
            @RequestParam(name = "file_kind", required = false) CloudFileKind fileKind,
            @RequestParam(required = false) CloudFileStatus status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(AdminFilePageResponse.from(
                cloudFileQueryService.list(page, pageSize, fileCode, fileKind, status, keyword)));
    }
}
