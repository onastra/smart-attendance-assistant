package com.lianzhi.attendance.attendance.util;

import java.math.BigDecimal;

/**
 * 球面距离计算（Haversine）。
 *
 * <p>用于判断打卡坐标是否落在场所允许的半径内。
 * 演示数据里的坐标是手动录入的，因此这里的判定只对"上报了坐标"的打卡生效。</p>
 */
public final class GeoDistanceUtil {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private GeoDistanceUtil() {
    }

    /** 返回两点间的球面距离（米）；任一坐标为 null 时返回 null，由调用方决定如何处理。 */
    public static Double distanceMeters(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) {
            return null;
        }
        double lon1 = Math.toRadians(lng1.doubleValue());
        double la1 = Math.toRadians(lat1.doubleValue());
        double lon2 = Math.toRadians(lng2.doubleValue());
        double la2 = Math.toRadians(lat2.doubleValue());
        double dLon = lon2 - lon1;
        double dLat = la2 - la1;
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(la1) * Math.cos(la2) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
