package com.lianzhi.attendance.attendance.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 围栏距离计算的单元测试。
 *
 * <p>这条逻辑决定"员工站在门口算不算到岗"，算错 200 米就足以把合规打卡误判为外勤。
 * 因此用已知距离做锚点校验，而不是只测"返回非负数"。</p>
 */
class GeoDistanceUtilTest {

    /** 同一坐标距离必须为 0 */
    @Test
    @DisplayName("同一点距离为 0")
    void samePointIsZero() {
        Double d = GeoDistanceUtil.distanceMeters(
                bd("116.4074"), bd("39.9042"), bd("116.4074"), bd("39.9042"));

        assertEquals(0.0, d, 0.5);
    }

    /**
     * 纬度每差 1 度约 111.19 km（子午线弧长）。
     * 用经度相同的两点验证，可以排除经度收敛带来的干扰。
     */
    @Test
    @DisplayName("纬度相差 1 度约为 111 公里")
    void oneDegreeLatitudeIsAbout111Km() {
        Double d = GeoDistanceUtil.distanceMeters(
                bd("116.4074"), bd("39.0000"), bd("116.4074"), bd("40.0000"));

        assertTrue(d > 110_000 && d < 112_000, "实际值：" + d);
    }

    /**
     * 北京天安门 → 上海人民广场，公认约 1067 公里。
     * 作为跨纬度大跨度校验，容差放宽到 ±15 公里（手填坐标本身有误差）。
     */
    @Test
    @DisplayName("北京到上海约 1067 公里")
    void beijingToShanghai() {
        Double d = GeoDistanceUtil.distanceMeters(
                bd("116.3975"), bd("39.9087"), bd("121.4737"), bd("31.2304"));

        assertTrue(d > 1_050_000 && d < 1_085_000, "实际值：" + d);
    }

    @Test
    @DisplayName("距离对称：A→B 与 B→A 相同")
    void distanceIsSymmetric() {
        Double ab = GeoDistanceUtil.distanceMeters(bd("116.40"), bd("39.90"), bd("121.47"), bd("31.23"));
        Double ba = GeoDistanceUtil.distanceMeters(bd("121.47"), bd("31.23"), bd("116.40"), bd("39.90"));

        assertEquals(ab, ba, 0.001);
    }

    /**
     * 任一坐标为 null 时返回 null，由调用方决定放行还是拒绝。
     * 这一点很关键：演示环境的场所坐标是手动录入的，为空是常态而非异常，
     * 若这里返回 0 会让"未配置坐标"的场所把所有打卡都判为围栏内。
     */
    @Test
    @DisplayName("坐标缺失返回 null，不得退化成 0 米")
    void nullCoordinateReturnsNull() {
        assertNull(GeoDistanceUtil.distanceMeters(null, bd("39.9"), bd("116.4"), bd("39.9")));
        assertNull(GeoDistanceUtil.distanceMeters(bd("116.4"), null, bd("116.4"), bd("39.9")));
        assertNull(GeoDistanceUtil.distanceMeters(bd("116.4"), bd("39.9"), null, bd("39.9")));
        assertNull(GeoDistanceUtil.distanceMeters(bd("116.4"), bd("39.9"), bd("116.4"), null));
    }

    @Test
    @DisplayName("极近距离：10 米内的偏移应被如实算出")
    void shortDistanceIsMeasurable() {
        // 纬度 0.0001 度约 11 米
        Double d = GeoDistanceUtil.distanceMeters(
                bd("116.407400"), bd("39.904200"), bd("116.407400"), bd("39.904300"));

        assertTrue(d > 8 && d < 15, "实际值：" + d);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
