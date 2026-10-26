package com.lianzhi.attendance.file.mapper;

import com.lianzhi.attendance.file.entity.SysFile;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_file 数据访问。
 */
public interface FileMapper {

    int insert(SysFile file);

    SysFile selectById(@Param("id") Long id);

    /** 我上传的文件（按时间倒序） */
    List<SysFile> selectByUploader(@Param("uploaderId") Long uploaderId);

    /** 管理员视角：全部文件 */
    List<SysFile> selectAll();

    int deleteById(@Param("id") Long id);
}
