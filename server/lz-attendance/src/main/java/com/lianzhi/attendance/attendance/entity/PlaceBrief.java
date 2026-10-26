package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡校验所需的最小场所信息。
 *
 * <p>跨模块只读：本模块不依赖 lz-place，直接读取同一库中的 biz_place 表。
 * 这样既避免了模块间编译期耦合，字段也只取校验真正需要的几个。</p>
 */
@Data
public class PlaceBrief {

    private Long id;
    private String placeName;

    /** 1 启用 0 停用；停用的场所不允许打卡 */
    private Integer status;

    private BigDecimal lng;
    private BigDecimal lat;

    /** 允许打卡半径（米） */
    private Integer coverRadiusM;
}
