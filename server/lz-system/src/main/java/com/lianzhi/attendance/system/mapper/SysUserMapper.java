package com.lianzhi.attendance.system.mapper;

import com.lianzhi.attendance.system.entity.SysUser;
import com.lianzhi.attendance.system.query.UserQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录账号数据访问。
 */
public interface SysUserMapper {

    SysUser selectById(@Param("id") Long id);

    SysUser selectByUsername(@Param("username") String username);

    List<SysUser> selectByQuery(UserQuery query);

    long countByQuery(UserQuery query);

    int insert(SysUser user);

    int updateById(SysUser user);

    /** 回写最近一次登录时间/IP，登录成功后调用 */
    int updateLoginInfo(@Param("id") Long id, @Param("ip") String ip);

    /** 判断账号是否已存在 */
    long countByUsername(@Param("username") String username, @Param("excludeId") Long excludeId);

    /** 清空该账号的全部角色关联（全量覆盖式授权的前半步） */
    int deleteUserRoles(@Param("userId") Long userId);

    /** 批量写入账号-角色关联 */
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
