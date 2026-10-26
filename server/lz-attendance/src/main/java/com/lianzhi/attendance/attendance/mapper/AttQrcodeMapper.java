package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.entity.AttQrcode;
import com.lianzhi.attendance.attendance.query.QrcodeQuery;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤二维码数据访问。
 */
public interface AttQrcodeMapper {

    int insert(AttQrcode qrcode);

    AttQrcode selectById(@Param("id") Long id);

    AttQrcode selectByBizId(@Param("bizId") String bizId);

    List<AttQrcode> selectByQuery(QrcodeQuery query);

    long countByQuery(QrcodeQuery query);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 标记一次性码已被使用 */
    int markUsed(@Param("id") Long id, @Param("usedTime") LocalDateTime usedTime);

    /**
     * 惰性过期：把已超过有效期但仍为 VALID 的记录置为 EXPIRED。
     *
     * <p>不依赖定时任务也能保证正确性——每次查询列表前先跑一次，
     * 阶段 6 引入定时任务后只是把这件事提前做，逻辑不用改。</p>
     */
    int markExpired(@Param("now") LocalDateTime now);

    /** 场所是否存在且启用 */
    long countUsablePlace(@Param("placeId") Long placeId);
}
