package com.lianzhi.attendance.attendance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 考勤业务参数。
 *
 * <p>口径说明（2026-10-26 与你确认后确定）：
 * 系统当前没有班次/考勤规则表，采用"全局统一 + 配置化"的口径——
 * 上下班时间与宽限分钟数集中在本类对应的配置项里，演示时可随时调整，
 * 后续若要支持按部门/岗位分别配置，只需把这里换成班次表查询，判定逻辑不用改。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lz.attendance")
public class AttendanceProperties {

    /** 上班时间（HH:mm） */
    private String workStartTime = "09:00";

    /** 下班时间（HH:mm） */
    private String workEndTime = "18:00";

    /** 迟到宽限：上班打卡晚于 workStartTime 且超过该分钟数记为 LATE */
    private int lateGraceMinutes = 15;

    /** 早退宽限：下班打卡早于 workEndTime 且超过该分钟数记为 EARLY */
    private int earlyLeaveGraceMinutes = 0;

    /** 重复打卡窗口：同一员工同一场所同一类型在该分钟数内的第二次提交视为重复 */
    private int duplicateWindowMinutes = 5;

    /**
     * 围栏校验开关。
     *
     * <p>true 表示"能拿到坐标才校验"：H5 端上报了经纬度就按场所半径判断，
     * 拿不到（浏览器未授权定位、PC 无定位模块）则跳过校验并在记录中标注，
     * 目的是让演示链路不会因为环境问题中断，同时不谎称做过校验。</p>
     */
    private boolean locationCheckEnabled = true;

    /** 动态码默认有效期（秒） */
    private int qrDynamicTtlSeconds = 120;

    /** 长期固定码默认有效期（秒）：24 小时 */
    private int qrFixedTtlSeconds = 86400;

    /** 前端自定义有效期时的上限（秒）：7 天，防止签发出永久有效的码 */
    private int qrMaxTtlSeconds = 604800;

    /** 二维码 PNG 边长（像素） */
    private int qrImageSize = 320;
}
