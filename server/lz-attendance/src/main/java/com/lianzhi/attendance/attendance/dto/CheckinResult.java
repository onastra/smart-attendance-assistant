package com.lianzhi.attendance.attendance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡结果。
 *
 * <p>失败原因通过异常携带 <code>reason</code> 返回（见 controller 的错误响应示例），
 * 这里只描述成功后的回显内容。</p>
 */
@Data
@Builder
public class CheckinResult {

    private Long recordId;
    private Long employeeId;
    private String empNo;
    private String employeeName;
    private String placeName;
    private String checkinType;
    private String checkinTypeText;
    private LocalDateTime checkinTime;
    private String result;
    private String resultText;
    private String source;
    private String remark;

    /** 本次提交命中幂等（同一 requestId 的重复请求），返回的是首次打卡的结果 */
    private boolean idempotent;

    /** 是否做过围栏校验：false 表示客户端没有上报坐标，服务端没有做距离判断 */
    private boolean locationChecked;

    /** 打卡点与场所的距离（米）；未做校验时为 null */
    private BigDecimal distanceMeters;

    /** 场所允许的半径（米） */
    private Integer radiusMeters;
}
