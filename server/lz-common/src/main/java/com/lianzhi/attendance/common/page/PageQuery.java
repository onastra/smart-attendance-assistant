package com.lianzhi.attendance.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询入参基类。
 *
 * <p>pageSize 上限设为 100，防止一次性拉取全表压垮数据库。</p>
 */
@Getter
@Setter
public class PageQuery {

    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer pageSize = 10;

    /** MyBatis PageHelper 未引入，这里直接换算偏移量，减少黑盒。 */
    public int offset() {
        return (pageNum - 1) * pageSize;
    }

    public int limit() {
        return pageSize;
    }

    // MyBatis XML 中的 #{offset} / #{limit} 走 getter 取值，这里提供同义访问器，
    // 避免在每个 Mapper 方法上重复声明 @Param 分页参数。
    public int getOffset() {
        return offset();
    }

    public int getLimit() {
        return limit();
    }
}
