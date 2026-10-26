package com.lianzhi.attendance.knowledge.query;

import com.lianzhi.attendance.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档列表查询条件。
 */
@Getter
@Setter
public class KbDocumentQuery extends PageQuery {

    private String title;
    private String status;
    private String visibility;
}
