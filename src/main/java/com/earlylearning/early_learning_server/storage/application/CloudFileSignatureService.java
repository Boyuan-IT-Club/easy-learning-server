package com.earlylearning.early_learning_server.storage.application;
import com.earlylearning.early_learning_server.storage.infrastructure.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.domain.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.domain.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.domain.CloudFile;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.domain.DownloadSignature;
import org.springframework.stereotype.Service;

/**
 * 批量签发只读下载地址：先校验全部编号，全部通过后统一签发，任一项不合格则整批失败。
 *
 * <p>权限：按角色限定可签发的资源范围；本模块不校验。
 */
@Service
public class CloudFileSignatureService {

    private static final int MAX_BATCH_SIZE = 100;

    /** 云端编号的命名空间。{@code LF_} 是平板本地自产文件，云端不存在，契约明令不可请求。 */
    private static final String CLOUD_CODE_PREFIX = "CF_";

    private final CloudFileMapper mapper;
    private final ObjectStorageService storage;

    public CloudFileSignatureService(CloudFileMapper mapper, ObjectStorageService storage) {
        this.mapper = mapper;
        this.storage = storage;
    }

    /** @return 领域结果；转成 HTTP 形状是 interfaces 层的事 */
    public List<DownloadSignature> sign(List<String> fileCodes) {
        requireValidBatch(fileCodes);

        Map<String, CloudFile> found = loadAll(fileCodes);
        // 校验阶段：任何一项不合格就整批失败，此时一个地址都还没签发。
        for (int index = 0; index < fileCodes.size(); index++) {
            requireSignable(fileCodes.get(index), found.get(fileCodes.get(index)), index);
        }

        // 签发阶段：顺序与请求一致，编号一一对应。
        List<DownloadSignature> items = new ArrayList<>(fileCodes.size());
        for (String fileCode : fileCodes) {
            items.add(signOne(found.get(fileCode)));
        }
        return List.copyOf(items);
    }

    /**
     * 请求级校验：数量、命名空间、去重。三者都在这里做，为的是让失败项能定位到具体的 JSON Pointer。
     *
     * <p>{@code LF_} 之所以是 400 而不是 404：请求体 schema 里每一项都要匹配 {@code ^CF_[A-Za-z0-9_-]+$}，
     * 所以那是请求本身不合法，还没到"资源存不存在"的问题。
     */
    private void requireValidBatch(List<String> fileCodes) {
        if (fileCodes == null || fileCodes.isEmpty() || fileCodes.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, null,
                    ApiErrorDetails.atField("/file_codes"));
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < fileCodes.size(); index++) {
            String fileCode = fileCodes.get(index);
            String fieldPath = "/file_codes/" + index;
            if (fileCode == null || !fileCode.startsWith(CLOUD_CODE_PREFIX)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, null,
                        ApiErrorDetails.atField(fieldPath));
            }
            if (!seen.add(fileCode)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, null,
                        ApiErrorDetails.atField(fieldPath));
            }
        }
    }

    private Map<String, CloudFile> loadAll(List<String> fileCodes) {
        Map<String, CloudFile> byCode = new LinkedHashMap<>();
        if (fileCodes.isEmpty()) {
            return byCode;
        }
        for (CloudFile file : mapper.selectList(
                new QueryWrapper<CloudFile>().in("file_code", fileCodes))) {
            byCode.put(file.getFileCode(), file);
        }
        return byCode;
    }

    /** 逐项校验：不存在 / 已删除 / 尚未就绪。每一项都带上是第几个失败，便于客户端定位。 */
    private void requireSignable(String fileCode, CloudFile file, int index) {
        String fieldPath = "/file_codes/" + index;
        if (file == null) {
            // 不存在的编号对调用者不可披露，因此只给位置不给编号。
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, null,
                    ApiErrorDetails.atField(fieldPath));
        }
        if (file.getStatus() == CloudFileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_DELETED, null, atItem(fieldPath, fileCode));
        }
        if (file.getStatus() != CloudFileStatus.READY) {
            // UPLOADING / INVALID：两种角色都不允许签发。
            throw new BusinessException(ErrorCode.RESOURCE_NOT_READY, null, atItem(fieldPath, fileCode));
        }
    }

    /** 资源对调用者可见时，同时回报它的编号，便于客户端对应上。 */
    private ApiErrorDetails atItem(String fieldPath, String fileCode) {
        return new ApiErrorDetails(fieldPath, fileCode, null, null, null, null, null);
    }

    private DownloadSignature signOne(CloudFile file) {
        ObjectStorageService.DownloadUrl signed = storage.generateDownloadUrl(file.getObjectKey());
        return new DownloadSignature(
                file.getFileCode(),
                signed.url().toString(),
                signed.expiresAt(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getMimeType());
    }
}
