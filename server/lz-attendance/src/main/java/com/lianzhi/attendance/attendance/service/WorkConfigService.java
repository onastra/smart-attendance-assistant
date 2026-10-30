package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.config.AttendanceProperties;
import com.lianzhi.attendance.attendance.entity.AttWorkConfig;
import com.lianzhi.attendance.attendance.mapper.AttWorkConfigMapper;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 全局上下班时间配置的读取与维护。
 *
 * <p>读取路径：att_work_config 单行 → 表为空时回退 yml 默认值。
 * 判定方（打卡即时判定 CheckinService、日汇总重算 DailyStatService）
 * 都经由本服务取值，保证两边口径永远一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkConfigService {

    private final AttWorkConfigMapper configMapper;
    private final AttendanceProperties properties;

    /** 读取当前生效配置；表为空/行缺失时用 yml 兜底，保证打卡链路永不因缺配置中断 */
    public AttWorkConfig get() {
        AttWorkConfig config = configMapper.selectOne();
        if (config != null) {
            return config;
        }
        AttWorkConfig fallback = new AttWorkConfig();
        fallback.setId(AttWorkConfig.SINGLETON_ID);
        fallback.setWorkStartTime(java.time.LocalTime.parse(properties.getWorkStartTime()));
        fallback.setWorkEndTime(java.time.LocalTime.parse(properties.getWorkEndTime()));
        fallback.setLateGraceMinutes(properties.getLateGraceMinutes());
        fallback.setEarlyLeaveGraceMinutes(properties.getEarlyLeaveGraceMinutes());
        return fallback;
    }

    /** 更新全局配置；校验放这里而不在 Controller，便于未来其他入口复用同一套规则 */
    public AttWorkConfig update(AttWorkConfig req, Long operatorId) {
        if (req.getWorkStartTime() == null || req.getWorkEndTime() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "必须指定上班与下班时间");
        }
        if (!req.getWorkEndTime().isAfter(req.getWorkStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "下班时间必须晚于上班时间");
        }
        req.setLateGraceMinutes(normalizeGrace(req.getLateGraceMinutes(), "迟到宽限"));
        req.setEarlyLeaveGraceMinutes(normalizeGrace(req.getEarlyLeaveGraceMinutes(), "早退宽限"));

        AttWorkConfig config = new AttWorkConfig();
        config.setWorkStartTime(req.getWorkStartTime());
        config.setWorkEndTime(req.getWorkEndTime());
        config.setLateGraceMinutes(req.getLateGraceMinutes());
        config.setEarlyLeaveGraceMinutes(req.getEarlyLeaveGraceMinutes());
        config.setUpdatedBy(operatorId);
        if (configMapper.update(config) == 0) {
            // 单例行被误删时自愈：插回默认行再更新（建表脚本里有 INSERT 种子，此处只是兜底）
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "配置行缺失，请先执行 docs/sql/postgresql/patch-worktime-qrcode-type.sql");
        }
        log.info("[SETTINGS] 更新全局考勤时间 start={} end={} lateGrace={} earlyGrace={} operator={}",
                config.getWorkStartTime(), config.getWorkEndTime(),
                config.getLateGraceMinutes(), config.getEarlyLeaveGraceMinutes(), operatorId);
        return get();
    }

    private int normalizeGrace(Integer value, String label) {
        if (value == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "分钟数不能为空");
        }
        if (value < 0 || value > 240) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "必须在 0~240 分钟之间");
        }
        return value;
    }
}
