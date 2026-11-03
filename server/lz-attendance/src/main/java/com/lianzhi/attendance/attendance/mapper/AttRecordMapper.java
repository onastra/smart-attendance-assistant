package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.entity.AttRecord;
import com.lianzhi.attendance.attendance.entity.AttRecordView;
import com.lianzhi.attendance.attendance.query.AttRecordQuery;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打卡记录数据访问。
 *
 * <p>只提供 insert 与 select：打卡流水不允许修改或删除。</p>
 */
public interface AttRecordMapper {

    int insert(AttRecord record);

    /** 幂等查询：同一 requestId 是否已落库 */
    long countByRequestId(@Param("requestId") String requestId);

    AttRecordView selectByRequestId(@Param("requestId") String requestId);

    /**
     * 重复打卡窗口内是否已有同类型记录。
     *
     * @param since 窗口起始时间（当前时间 - duplicateWindowMinutes）
     */
    long countRecent(@Param("employeeId") Long employeeId,
                     @Param("placeId") Long placeId,
                     @Param("checkinType") String checkinType,
                     @Param("since") LocalDateTime since);

    /**
     * 重复打卡窗口内是否已有同类型记录（不限场所）。
     * 人脸打卡通道没有二维码，场所由员工选择，防重口径必须跨通道、跨场所：
     * 扫码打了上班卡后 5 分钟内刷脸再打上班卡同样要被拦截。
     */
    long countRecentAnyPlace(@Param("employeeId") Long employeeId,
                             @Param("checkinType") String checkinType,
                             @Param("since") LocalDateTime since);

    List<AttRecordView> selectByQuery(AttRecordQuery query);

    long countByQuery(AttRecordQuery query);
}
