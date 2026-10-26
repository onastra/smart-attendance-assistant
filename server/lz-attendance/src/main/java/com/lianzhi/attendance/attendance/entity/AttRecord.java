package com.lianzhi.attendance.attendance.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡流水。
 *
 * <p>设计约束：打卡记录一旦产生不允许硬删除（历史考勤是核算依据），
 * 因此本模块只提供新增与查询，不提供 update/delete。</p>
 */
@Data
public class AttRecord {

    /** 打卡类型：上班 */
    public static final String TYPE_ON_DUTY = "ON_DUTY";
    /** 打卡类型：下班 */
    public static final String TYPE_OFF_DUTY = "OFF_DUTY";
    /** 打卡类型：外勤 */
    public static final String TYPE_FIELD = "FIELD";

    /** 结果：正常 */
    public static final String RESULT_NORMAL = "NORMAL";
    /** 结果：迟到 */
    public static final String RESULT_LATE = "LATE";
    /** 结果：早退 */
    public static final String RESULT_EARLY = "EARLY";
    /** 结果：异常（如超出围栏但管理员放行） */
    public static final String RESULT_ABNORMAL = "ABNORMAL";

    /** 来源：H5 扫码/手动输入 */
    public static final String SOURCE_H5 = "H5";
    /** 来源：后台补录 */
    public static final String SOURCE_ADMIN = "ADMIN";
    /** 来源：模拟人脸识别（阶段 5 启用） */
    public static final String SOURCE_FACE_MOCK = "FACE_MOCK";

    private Long id;
    private Long employeeId;
    private Long placeId;
    private Long qrcodeId;
    private String checkinType;
    private LocalDateTime checkinTime;
    private String source;
    private String result;
    private BigDecimal lng;
    private BigDecimal lat;
    private String clientIp;

    /** 幂等请求号：网络重试或双击提交不会产生第二条记录 */
    private String requestId;

    private String remark;
    private LocalDateTime createdAt;
}
