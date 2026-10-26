package com.lianzhi.attendance.attendance.dto;

import lombok.Data;

/**
 * 场所时段分布：一个场所某个小时内的打卡次数。
 *
 * <p>需求原文提到"场所热力图"，但没有地图服务 Key 时画不出真正的地图图层。
 * 这里如实返回时段分布数据（哪个场所几点最挤），不做地图渲染——
 * 有 Key 之后前端换成地图叠加层即可，数据不用重做。</p>
 */
@Data
public class PlaceHourItem {

    private Long placeId;
    private String placeName;

    /** 0 ~ 23 */
    private int hour;

    private long count;
}
