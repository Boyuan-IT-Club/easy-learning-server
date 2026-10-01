package com.earlylearning.early_learning_server.common.web;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 管理端列表的分页参数：契约统一为 {@code page} ≥ 1、{@code page_size} 1–100，越界返回 400，
 * {@code details.field_path} 按契约写成 {@code /parameters/参数名}。
 */
public record PageParams(int page, int pageSize) {

    public static final int MAX_PAGE_SIZE = 100;

    public static PageParams of(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/page"));
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/parameters/page_size"));
        }
        return new PageParams(page, pageSize);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }

    /** LIKE 的通配符按普通字符处理（契约），配合 SQL 里的 {@code ESCAPE '!'}。 */
    public static String containsPattern(String keyword) {
        String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
