package com.lianzhi.attendance.attendance.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二维码列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QrcodeQuery extends PageQuery {

    private Long placeId;

    /** VALID / INVALID / EXPIRED / USED */
    private String status;

    /** DYNAMIC / FIXED */
    private String qrType;
}
