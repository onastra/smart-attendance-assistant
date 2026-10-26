package com.lianzhi.attendance.attendance.mapper;

import com.lianzhi.attendance.attendance.entity.PlaceBrief;
import org.apache.ibatis.annotations.Param;

/**
 * 考勤模块的跨模块只读数据访问。
 *
 * <p>打卡需要校验"场所是否存在且启用""该员工是否绑定了这个场所""员工是否在职"，
 * 这些数据分别属于 lz-place 与 lz-system 的表。模块化单体的约定是模块之间不产生
 * 编译期依赖（见 docs/architecture.md），因此这里直接读同一库中的物理表，
 * 只取校验必需的字段，不引入其他模块的实体类。</p>
 */
public interface AttSupportMapper {

    PlaceBrief selectPlaceBrief(@Param("placeId") Long placeId);

    /** 员工-场所的有效绑定数量 */
    long countBinding(@Param("employeeId") Long employeeId, @Param("placeId") Long placeId);

    /** 在职员工数量（status = 1） */
    long countActiveEmployee(@Param("employeeId") Long employeeId);

    /** 员工姓名，用于打卡结果回显 */
    String selectEmployeeName(@Param("employeeId") Long employeeId);

    /**
     * 员工所属部门。
     *
     * <p>用途：管理员查考勤时确定数据范围——部门主管只能看本部门。
     * 没有部门归属时返回 null，调用方按"只能看本人"处理。</p>
     */
    Long selectEmployeeDeptId(@Param("employeeId") Long employeeId);

    /** 操作日志：关键操作（签发二维码、打卡、失效）留痕 */
    int insertOperationLog(@Param("userId") Long userId,
                           @Param("module") String module,
                           @Param("action") String action,
                           @Param("targetId") String targetId,
                           @Param("ip") String ip,
                           @Param("success") int success,
                           @Param("message") String message);
}
