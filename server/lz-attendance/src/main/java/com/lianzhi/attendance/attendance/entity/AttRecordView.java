package com.lianzhi.attendance.attendance.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡记录的展示视图：在流水字段基础上补齐员工与场所的名称信息。
 *
 * <p>与 {@link AttRecord} 分开是为了让"写模型"保持干净——
 * 接口入参只需要几个 ID，出参才需要联表后的可读字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttRecordView extends AttRecord {

    private String empNo;
    private String employeeName;
    private String deptName;
    private String placeName;

    /** 结果的可读文案（正常/迟到/早退/异常） */
    private String resultText;

    /** 打卡类型的可读文案（上班/下班/外勤） */
    private String checkinTypeText;
}
