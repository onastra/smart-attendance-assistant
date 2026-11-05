package com.lianzhi.attendance.knowledge.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档列表查询条件。
 *
 * <p>可见性过滤三件套（{@code adminView} / {@code viewerUserId} / {@code viewerDeptId}）
 * 由服务端按当前登录人填入，不接受前端传参——前端传了也会被覆盖，
 * 保证"列表只显示该用户可见的文档"这个口径不会被请求参数绕开。</p>
 */
@Getter
@Setter
public class KbDocumentQuery extends PageQuery {

    private String title;
    private String status;
    private String visibility;

    /** 管理员视角：不过滤，可见全部文档（含他人 PRIVATE） */
    private Boolean adminView;
    /** 当前用户 id，用于 PRIVATE（仅自己）过滤 */
    private Long viewerUserId;
    /** 当前用户部门 id，用于 DEPT（指定部门）过滤 */
    private Long viewerDeptId;
}
