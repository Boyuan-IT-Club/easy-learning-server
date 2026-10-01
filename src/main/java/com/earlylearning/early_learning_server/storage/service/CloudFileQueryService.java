package com.earlylearning.early_learning_server.storage.service;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.entity.CloudFileKind;
import com.earlylearning.early_learning_server.entity.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileQueryMapper;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.dto.CloudFileResponse;
import com.earlylearning.early_learning_server.storage.model.FilePage;
import com.earlylearning.early_learning_server.storage.model.FileSummary;
import org.springframework.stereotype.Service;

/**
 * 文件查询：按编号取元数据，以及带筛选与引用数的分页列表。
 *
 * <p>权限：契约对列表要求管理员凭证、对元数据按角色区分可见范围；本模块都不校验，
 * 元数据按教师语义执行状态规则（非 READY 不可读）。
 */
@Service
public class CloudFileQueryService {

    private static final int MIN_PAGE = 1;
    private static final int MIN_PAGE_SIZE = 1;
    /** 契约：{@code page_size} 最大 100。 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * LIKE 的转义符用 {@code !} 而不用反斜杠。
     *
     * <p>MySQL 在字符串字面量里会把 {@code \%} 退化成 {@code %}（反斜杠被忽略），"转义"因此会悄悄失效；
     * {@code !} 在 MySQL 的字符串里没有特殊含义，配合显式 {@code ESCAPE '!'} 才稳定。
     */
    private static final char LIKE_ESCAPE = '!';

    private final CloudFileMapper mapper;
    private final CloudFileQueryMapper queryMapper;

    public CloudFileQueryService(CloudFileMapper mapper, CloudFileQueryMapper queryMapper) {
        this.mapper = mapper;
        this.queryMapper = queryMapper;
    }

    /** @return 分页读模型；转成 HTTP 形状是 controller 层的事 */
    public FilePage list(int page,
                                      int pageSize,
                                      String fileCode,
                                      CloudFileKind fileKind,
                                      CloudFileStatus status,
                                      String keyword) {
        if (page < MIN_PAGE || pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedCode = hasText(fileCode) ? fileCode : null;
        String kindValue = fileKind == null ? null : fileKind.value();
        String statusValue = status == null ? null : status.value();
        // 通配符按普通字符处理：不转义的话，搜 "%" 会命中全部记录。
        String pattern = hasText(keyword) ? "%" + escapeLikeWildcards(keyword) + "%" : null;

        long total = queryMapper.countMatching(normalizedCode, kindValue, statusValue, pattern);
        long offset = (long) (page - 1) * pageSize;
        List<CloudFile> rows = queryMapper.selectPage(normalizedCode, kindValue, statusValue, pattern,
                pageSize, offset);

        Map<Integer, Integer> referenceCounts = referenceCounts(rows);
        List<FileSummary> items = rows.stream()
                .map(file -> FileSummary.from(file, referenceCounts.getOrDefault(file.getId(), 0)))
                .toList();
        return new FilePage(items, page, pageSize, total);
    }

    /**
     * 按编号取元数据，按教师语义执行状态规则：DELETED 抛 410，其余非 READY 抛 409。
     *
     * <p>DELETED 必须先判——它不是 READY，但状态码是 410 而非 409。
     */
    /**
     * 按编号取文件实体（含 {@code objectKey} 等内部字段），供模块内部读取内容使用。
     *
     * <p>校验语义与签发、元数据接口保持一致：不存在 404、已删除 410、未就绪 409。
     * 否则会出现"评分能拿到图、下载却拿不到"这类最难查的不一致。
     */
    public CloudFile requireReadable(String fileCode) {
        CloudFile file = mapper.selectOne(new QueryWrapper<CloudFile>().eq("file_code", fileCode));
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, ApiErrorDetails.atFile(fileCode));
        }
        if (file.getStatus() == CloudFileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_DELETED, ApiErrorDetails.atFile(fileCode));
        }
        if (file.getStatus() != CloudFileStatus.READY) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_READY, ApiErrorDetails.atFile(fileCode));
        }
        return file;
    }

    /** @return 文件实体；转成 HTTP 形状是 controller 层的事 */
    public CloudFile metadata(String fileCode) {
        CloudFile file = mapper.selectOne(new QueryWrapper<CloudFile>().eq("file_code", fileCode));
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (file.getStatus() == CloudFileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_DELETED);
        }
        if (file.getStatus() != CloudFileStatus.READY) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_READY);
        }
        return file;
    }

    /** 只对当前页做一次引用统计，避免为整张表跑相关子查询。 */
    private Map<Integer, Integer> referenceCounts(List<CloudFile> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = files.stream().map(CloudFile::getId).toList();
        Map<Integer, Integer> counts = new HashMap<>();
        for (CloudFileQueryMapper.ReferenceCount row : queryMapper.countReferences(ids)) {
            counts.put(row.id(), row.referenceCount());
        }
        return counts;
    }

    /** 转义 LIKE 的通配符，让 {@code %}、{@code _} 与转义符本身按字面匹配。 */
    private String escapeLikeWildcards(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        for (int i = 0; i < keyword.length(); i++) {
            char current = keyword.charAt(i);
            if (current == LIKE_ESCAPE || current == '%' || current == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
