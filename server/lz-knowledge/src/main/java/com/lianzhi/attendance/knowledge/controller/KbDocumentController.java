package com.lianzhi.attendance.knowledge.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthContext;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.knowledge.dto.DocumentView;
import com.lianzhi.attendance.knowledge.entity.KbDocument;
import com.lianzhi.attendance.knowledge.query.KbDocumentQuery;
import com.lianzhi.attendance.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库文档接口。
 *
 * <p>权限拆分与统计模块同一套思路：列表给所有人（便于知道库里有什么），
 * 但上传 / 删除 / 重建是写操作，分别用独立权限码控制。</p>
 */
@Tag(name = "知识库文档", description = "上传、解析、切片、检索索引与删除重建")
@RestController
@RequiredArgsConstructor
public class KbDocumentController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "上传文档", description = "支持 PDF/DOCX/XLSX/PPTX/DOC/XLS/PPT/TXT/MD；解析失败会置为 FAILED 并写明原因")
    @PostMapping("/api/kb/documents")
    @RequirePermission("kb:doc:upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "visibility", required = false) String visibility,
                                         @RequestParam(value = "ownerDeptId", required = false) Long ownerDeptId) {
        AuthUser user = AuthContext.get();
        KbDocument document = knowledgeService.upload(file, visibility, ownerDeptId, user);
        Map<String, Object> data = new HashMap<>();
        data.put("document", document);
        return R.ok(data);
    }

    @Operation(summary = "文档列表", description = "带 canRead 标记，提示当前用户是否有权阅读该文档")
    @GetMapping("/api/kb/documents")
    @RequirePermission("kb:doc:list")
    public R<PageResult<DocumentView>> list(KbDocumentQuery query) {
        AuthUser user = AuthContext.get();
        return R.ok(knowledgeService.list(query, user));
    }

    @Operation(summary = "删除文档", description = "连带删除切片与原文件，不可恢复")
    @DeleteMapping("/api/kb/documents/{id}")
    @RequirePermission("kb:doc:delete")
    public R<Void> delete(@PathVariable("id") Long id) {
        knowledgeService.delete(id, AuthContext.get());
        return R.ok();
    }

    @Operation(summary = "重建索引", description = "用留存的原文件重新解析与切片，用于调整切片参数或修复解析失败")
    @PostMapping("/api/kb/documents/{id}/rebuild")
    @RequirePermission("kb:doc:rebuild")
    public R<Map<String, Object>> rebuild(@PathVariable("id") Long id) {
        KbDocument document = knowledgeService.rebuild(id, AuthContext.get());
        Map<String, Object> data = new HashMap<>();
        data.put("document", document);
        return R.ok(data);
    }

    @Operation(summary = "修改可见范围", description = "PUBLIC / DEPT（需 ownerDeptId）/ PRIVATE")
    @PutMapping("/api/kb/documents/{id}/visibility")
    @RequirePermission("kb:doc:upload")
    public R<Map<String, Object>> updateVisibility(@PathVariable("id") Long id,
                                                   @RequestParam("visibility") String visibility,
                                                   @RequestParam(value = "ownerDeptId", required = false) Long ownerDeptId) {
        KbDocument document = knowledgeService.updateVisibility(id, visibility, ownerDeptId, AuthContext.get());
        Map<String, Object> data = new HashMap<>();
        data.put("document", document);
        return R.ok(data);
    }
}
