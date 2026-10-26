package com.lianzhi.attendance.file.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 文件模块的跨模块只读数据访问。
 *
 * <p>判定"同部门可见"需要知道当前用户属于哪个部门，而员工表属于 lz-system。
 * 模块化单体的约定是模块之间不产生编译期依赖，因此这里直接读同一库中的物理表，
 * 只取判定必需的字段，与 AttSupportMapper 的做法保持一致。</p>
 */
public interface FileSupportMapper {

    /** 员工所属部门；无归属时返回 null */
    Long selectEmployeeDeptId(@Param("employeeId") Long employeeId);

    /** 操作日志：下载/删除留痕，便于追溯谁动过哪个文件 */
    int insertOperationLog(@Param("userId") Long userId,
                           @Param("module") String module,
                           @Param("action") String action,
                           @Param("targetId") String targetId,
                           @Param("ip") String ip,
                           @Param("success") int success,
                           @Param("message") String message);
}
