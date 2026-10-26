package com.lianzhi.attendance.common.page;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页出参。
 */
@Getter
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int pageNum;
    private final int pageSize;
    private final long total;
    private final int pages;
    private final List<T> list;

    private PageResult(int pageNum, int pageSize, long total, List<T> list) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list == null ? Collections.emptyList() : list;
        this.pages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
    }

    public static <T> PageResult<T> of(PageQuery query, long total, List<T> list) {
        return new PageResult<>(query.getPageNum(), query.getPageSize(), total, list);
    }

    /** 空数据场景返回空集合而不是 null，前端无需额外判空。 */
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(query.getPageNum(), query.getPageSize(), 0L, Collections.emptyList());
    }

    public <V> PageResult<V> map(Function<T, V> mapper) {
        List<V> mapped = this.list.stream().map(mapper).toList();
        return new PageResult<>(this.pageNum, this.pageSize, this.total, mapped);
    }
}
