package com.earlylearning.early_learning_server.common.web;

import java.util.List;
import java.util.function.Function;

import com.earlylearning.early_learning_server.common.paging.PageResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 契约里各分页响应的统一形状：{@code {items, page, page_size, total}}。 */
public record PageResponse<T>(@JsonProperty("items") List<T> items,
                              @JsonProperty("page") int page,
                              @JsonProperty("page_size") int pageSize,
                              @JsonProperty("total") long total) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<? super S, ? extends T> mapper) {
        return new PageResponse<>(result.items().stream().<T>map(mapper).toList(),
                result.page(), result.pageSize(), result.total());
    }
}
