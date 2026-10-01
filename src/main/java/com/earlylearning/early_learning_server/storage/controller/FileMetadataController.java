package com.earlylearning.early_learning_server.storage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.storage.dto.CloudFileResponse;
import com.earlylearning.early_learning_server.storage.dto.DownloadSignatureBatchResponse;
import com.earlylearning.early_learning_server.storage.dto.SignDownloadUrlsRequest;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.service.CloudFileSignatureService;

/**
 * 教师端的文件读取接口：元数据与批量下载地址签发。
 *
 * <p>权限：契约按角色限定可见与可签发的资源范围；本模块不校验。
 */
@RestController
public class FileMetadataController {

    private final CloudFileQueryService cloudFileQueryService;
    private final CloudFileSignatureService cloudFileSignatureService;

    public FileMetadataController(CloudFileQueryService cloudFileQueryService,
                                  CloudFileSignatureService cloudFileSignatureService) {
        this.cloudFileQueryService = cloudFileQueryService;
        this.cloudFileSignatureService = cloudFileSignatureService;
    }

    @GetMapping("/api/files/{file_code}")
    public ApiResponse<CloudFileResponse> metadata(@PathVariable("file_code") String fileCode) {
        return ApiResponse.ok(CloudFileResponse.from(cloudFileQueryService.metadata(fileCode)));
    }

    /**
     * 批量签发只读下载地址。
     *
     * <p>整批语义：先全部校验、再统一签发，任意一项失败则整批失败（见 {@link CloudFileSignatureService}）。
     */
    @PostMapping("/api/files/signatures")
    public ApiResponse<DownloadSignatureBatchResponse> sign(@RequestBody SignDownloadUrlsRequest request) {
        return ApiResponse.ok(DownloadSignatureBatchResponse.from(cloudFileSignatureService.sign(request.fileCodes())));
    }
}
