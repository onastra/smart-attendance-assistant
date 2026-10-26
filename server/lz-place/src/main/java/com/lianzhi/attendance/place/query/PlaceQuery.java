package com.lianzhi.attendance.place.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 场所分页查询条件。
 */
@Getter
@Setter
public class PlaceQuery extends PageQuery {

    private String placeName;
    private Long companyId;
    private Integer status;
}
