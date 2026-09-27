package com.earlylearning.early_learning_server.storage.web;

import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.storage.CloudFileKind;
import com.earlylearning.early_learning_server.storage.CloudFileDeletionService;
import com.earlylearning.early_learning_server.storage.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.CloudFileService;
import com.earlylearning.early_learning_server.storage.CloudFileStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 官方文件的管理端接口：上传、列表、标记删除。
 *
 * <p>权限：契约要求管理员凭证；本模块不校验。
 */
@RestController
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
     * <p>是**标记**不是物理删除：只把状态改成 `DELETED`，对象本身留在存储里
     * （契约：仅从未发布的孤儿对象可由后台按宽限期清理）。
     */
    @PostMapping("/admin/files/{file_code}/delete")
    public ApiResponse<CloudFileResponse> markDeleted(@PathVariable("file_code") String fileCode) {
        return ApiResponse.ok(cloudFileDeletionService.markDeleted(fileCode));
    }

    @PostMapping(path = "/admin/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            // file_kind / file_name 是 multipart 的**表单字段**，不是文件部分：
            // 用 @RequestPart 会让 Spring 拿消息转换器去解枚举，客户端会收到 415。
            @RequestParam("file_kind") CloudFileKind fileKind,
            @RequestParam(value = "file_name", required = false) String fileName) {
        StoredResponse stored = cloudFileService.upload(idempotencyKey, file, fileKind, fileName);
        return ResponseEntity.status(stored.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(stored.body());
    }

    /**
     * 分页查询官方文件与引用数。
     *
     * <p>`file_code` 是精确匹配，契约用它替代单独的详情接口；`keyword` 对文件名与编号做包含匹配。
     */
    @GetMapping("/admin/files")
    public ApiResponse<AdminFilePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "file_code", required = false) String fileCode,
            @RequestParam(name = "file_kind", required = false) CloudFileKind fileKind,
            @RequestParam(required = false) CloudFileStatus status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(cloudFileQueryService.list(page, pageSize, fileCode, fileKind, status, keyword));
    }
}
