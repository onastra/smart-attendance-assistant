package com.lianzhi.attendance.knowledge.mapper;

import com.lianzhi.attendance.knowledge.entity.KbDocument;
import com.lianzhi.attendance.knowledge.query.KbDocumentQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档持久化。
 */
public interface KbDocumentMapper {

    int insertDocument(KbDocument document);

    KbDocument selectById(@Param("id") Long id);

    List<KbDocument> selectPage(KbDocumentQuery query);

    long countPage(KbDocumentQuery query);

    /**
     * 解析状态回写。切片数由调用方算好一起写，避免再发一条 UPDATE。
     */
    int updateParseResult(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("chunkCount") Integer chunkCount,
                          @Param("parseMessage") String parseMessage);

    int updateVisibility(@Param("id") Long id,
                         @Param("visibility") String visibility,
                         @Param("ownerDeptId") Long ownerDeptId);

    int deleteById(@Param("id") Long id);

    List<KbDocument> selectByHash(@Param("contentHash") String contentHash);

    /**
     * 提问人的部门，用于 DEPT 可见范围过滤。
     *
     * <p>知识库模块不依赖考勤模块，这里自己查，避免为了一个字段把模块耦合起来。</p>
     */
    Long selectEmployeeDeptId(@Param("employeeId") Long employeeId);
}
