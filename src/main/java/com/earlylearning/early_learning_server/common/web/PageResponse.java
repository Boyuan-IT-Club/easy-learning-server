package com.earlylearning.early_learning_server.common.web;

import java.util.List;
import java.util.function.Function;

import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约里各分页响应的统一形状：{@code {items, page, page_size, total}}。 */
public record PageResponse<T>(@JsonProperty("items") List<T> items,
                              @JsonProperty("page") int page,
                              @JsonProperty("page_size") int pageSize,
                              @JsonProperty("total") long total) {

    /** @param rows 当前页的原始记录，由 {@code toItem} 转成响应项 */
    public static <S, T> PageResponse<T> of(PageQuery query, List<S> rows, long total,
                                            Function<? super S, ? extends T> toItem) {
        return new PageResponse<>(rows.stream().<T>map(toItem).toList(), query.page(), query.pageSize(), total);
    }
}
