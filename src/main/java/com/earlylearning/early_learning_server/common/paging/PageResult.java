package com.earlylearning.early_learning_server.common.paging;

import java.util.List;
import java.util.function.Function;

/** 一页结果：当前页记录与过滤后的总条数。 */
public record PageResult<T>(List<T> items, int page, int pageSize, long total) {

    public static <T> PageResult<T> of(PageQuery query, List<T> items, long total) {
        return new PageResult<>(List.copyOf(items), query.page(), query.pageSize(), total);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), page, pageSize, total);
    }
}
