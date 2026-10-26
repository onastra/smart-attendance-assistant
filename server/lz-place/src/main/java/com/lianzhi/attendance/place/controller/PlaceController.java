package com.lianzhi.attendance.place.controller;

import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.auth.RequirePermission;
import com.lianzhi.attendance.place.entity.Place;
import com.lianzhi.attendance.place.entity.PlaceEmployee;
import com.lianzhi.attendance.place.query.PlaceQuery;
import com.lianzhi.attendance.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 办公场所接口。
 */
@Tag(name = "办公场所", description = "场所增删改查、场所码生成与员工绑定")
@Validated
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "场所分页列表")
    @GetMapping
    @RequirePermission("place:list")
    public R<PageResult<Place>> page(PlaceQuery query) {
        return R.ok(placeService.page(query));
    }

    @Operation(summary = "场所详情")
    @GetMapping("/{id}")
    @RequirePermission("place:list")
    public R<Place> detail(@PathVariable Long id) {
        return R.ok(placeService.detail(id));
    }

    @Operation(summary = "地图能力状态", description = "如实上报是否接入地图服务；未接入时坐标只能手动录入")
    @GetMapping("/map-status")
    @RequirePermission("place:list")
    public R<Map<String, Object>> mapStatus() {
        return R.ok(placeService.mapStatus());
    }

    @Operation(summary = "新建场所", description = "placeCode 由服务端生成，不接受外部传入")
    @PostMapping
    @RequirePermission("place:create")
    public R<Map<String, Object>> create(@Valid @RequestBody Place place) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", placeService.create(place));
        return R.ok(data);
    }

    @Operation(summary = "编辑场所", description = "placeCode 一经生成不可修改")
    @PutMapping("/{id}")
    @RequirePermission("place:update")
    public R<Void> update(@PathVariable Long id, @RequestBody Place place) {
        place.setId(id);
        placeService.update(place);
        return R.ok();
    }

    @Operation(summary = "启用/停用场所", description = "仍有绑定员工时不允许停用")
    @PostMapping("/{id}/status")
    @RequirePermission("place:update")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return R.fail(ResultCode.PARAM_ERROR, "status 只能为 0（停用）或 1（启用）");
        }
        placeService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "场所下已绑定员工")
    @GetMapping("/{id}/employees")
    @RequirePermission("place:list")
    public R<List<PlaceEmployee>> employees(@PathVariable Long id) {
        return R.ok(placeService.employeesOf(id));
    }

    @Operation(summary = "绑定员工到场所")
    @PostMapping("/{id}/employees")
    @RequirePermission("place:bind")
    public R<Void> bindEmployees(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> employeeIds = body.get("employeeIds");
        placeService.bindEmployees(id, employeeIds);
        return R.ok();
    }

    @Operation(summary = "解绑员工")
    @DeleteMapping("/{id}/employees/{employeeId}")
    @RequirePermission("place:bind")
    public R<Void> unbindEmployee(@PathVariable Long id, @PathVariable Long employeeId) {
        placeService.unbindEmployee(id, employeeId);
        return R.ok();
    }

    @Operation(summary = "重新生成场所码")
    @PostMapping("/{id}/regenerate-code")
    @RequirePermission("place:update")
    public R<Map<String, Object>> regenerateCode(@PathVariable Long id) {
        return R.ok(Map.of("placeCode", placeService.regenerateCode(id)));
    }
}
