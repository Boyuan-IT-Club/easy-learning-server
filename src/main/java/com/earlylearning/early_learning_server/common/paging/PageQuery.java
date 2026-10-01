package com.earlylearning.early_learning_server.common.paging;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 分页参数（契约：page 从 1 开始；page_size 默认 20、最大 100）。分页一律显式 LIMIT/OFFSET。
 */
public record PageQuery(int page, int pageSize) {

    public static final int MAX_PAGE_SIZE = 100;

    /** LIKE 的转义符用 {@code !}：MySQL 字符串字面量里的反斜杠会被吃掉，转义悄悄失效。 */
    private static final char LIKE_ESCAPE = '!';

    /** @throws BusinessException 400，field_path 为 /parameters/page 或 /parameters/page_size */
    public static PageQuery of(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/page"));
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/page_size"));
        }
        return new PageQuery(page, pageSize);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }

    /** 包含匹配的 LIKE 模式：{@code %}、{@code _} 与转义符按普通字符处理。配合 {@code ESCAPE '!'} 使用。 */
    public static String containsPattern(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length() + 8).append('%');
        for (int i = 0; i < keyword.length(); i++) {
            char current = keyword.charAt(i);
            if (current == LIKE_ESCAPE || current == '%' || current == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(current);
        }
        return escaped.append('%').toString();
    }
}
