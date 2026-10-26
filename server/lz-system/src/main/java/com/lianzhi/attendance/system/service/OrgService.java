package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.system.entity.Company;
import com.lianzhi.attendance.system.entity.Department;
import com.lianzhi.attendance.system.entity.Position;
import com.lianzhi.attendance.system.mapper.CompanyMapper;
import com.lianzhi.attendance.system.mapper.DepartmentMapper;
import com.lianzhi.attendance.system.mapper.EmployeeMapper;
import com.lianzhi.attendance.system.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织架构：公司、部门、岗位。
 *
 * <p>删除策略统一为"有引用就拒绝"，不允许级联删除：
 * 组织节点一旦消失，挂在其下的员工、考勤记录都会失去归属，
 * 这类破坏在演示时不易察觉，一旦发生很难恢复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgService {

    private final CompanyMapper companyMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final EmployeeMapper employeeMapper;

    // ==================== 公司 ====================

    public List<Company> listCompanies() {
        return companyMapper.selectAll();
    }

    @Transactional
    public Long createCompany(Company company) {
        if (companyMapper.countByCode(company.getCompanyCode(), null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "公司编码已存在");
        }
        if (company.getParentId() == null) {
            company.setParentId(0L);
        }
        if (company.getStatus() == null) {
            company.setStatus(1);
        }
        companyMapper.insert(company);
        log.info("[ORG] 新建公司 id={} name={}", company.getId(), company.getCompanyName());
        return company.getId();
    }

    @Transactional
    public void updateCompany(Company company) {
        requireCompany(company.getId());
        companyMapper.updateById(company);
        log.info("[ORG] 编辑公司 id={}", company.getId());
    }

    @Transactional
    public void deleteCompany(Long id) {
        requireCompany(id);
        if (companyMapper.countChildrenByParentId(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该公司下仍有子公司，无法删除");
        }
        Company patch = new Company();
        patch.setId(id);
        patch.setStatus(0);
        companyMapper.updateById(patch);
        log.info("[ORG] 停用公司 id={}", id);
    }

    // ==================== 部门 ====================

    public List<Department> listDepartments(Long companyId) {
        return companyId == null ? departmentMapper.selectAll() : departmentMapper.selectByCompanyId(companyId);
    }

    /**
     * 部门树。
     *
     * <p>在内存中一次性组装，避免递归查询数据库产生 N+1。</p>
     */
    public List<Department> departmentTree(Long companyId) {
        List<Department> all = listDepartments(companyId);
        Map<Long, Department> index = new LinkedHashMap<>();
        for (Department dept : all) {
            index.put(dept.getId(), dept);
        }
        List<Department> roots = new ArrayList<>();
        for (Department dept : all) {
            Long parentId = dept.getParentId();
            Department parent = parentId == null || parentId == 0 ? null : index.get(parentId);
            if (parent == null || parent.getId().equals(dept.getId())) {
                roots.add(dept);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }

    @Transactional
    public Long createDepartment(Department dept) {
        if (!StringUtils.hasText(dept.getDeptName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "部门名称不能为空");
        }
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        if (dept.getSortNo() == null) {
            dept.setSortNo(0);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        departmentMapper.insert(dept);
        log.info("[ORG] 新建部门 id={} name={}", dept.getId(), dept.getDeptName());
        return dept.getId();
    }

    @Transactional
    public void updateDepartment(Department dept) {
        requireDepartment(dept.getId());
        departmentMapper.updateById(dept);
        log.info("[ORG] 编辑部门 id={}", dept.getId());
    }

    @Transactional
    public void deleteDepartment(Long id) {
        requireDepartment(id);
        if (departmentMapper.countChildrenByParentId(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该部门下仍有子部门，请先删除子部门");
        }
        if (employeeMapper.countActiveByDeptId(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该部门下仍有在职员工，请先调整员工归属");
        }
        Department patch = new Department();
        patch.setId(id);
        patch.setStatus(0);
        departmentMapper.updateById(patch);
        log.info("[ORG] 停用部门 id={}", id);
    }

    // ==================== 岗位 ====================

    public List<Position> listPositions(Long companyId) {
        return positionMapper.selectByCompanyId(companyId);
    }

    @Transactional
    public Long createPosition(Position position) {
        if (!StringUtils.hasText(position.getPositionName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "岗位名称不能为空");
        }
        if (position.getStatus() == null) {
            position.setStatus(1);
        }
        positionMapper.insert(position);
        log.info("[ORG] 新建岗位 id={} name={}", position.getId(), position.getPositionName());
        return position.getId();
    }

    @Transactional
    public void updatePosition(Position position) {
        Position exist = positionMapper.selectById(position.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "岗位不存在");
        }
        positionMapper.updateById(position);
        log.info("[ORG] 编辑岗位 id={}", position.getId());
    }

    @Transactional
    public void deletePosition(Long id) {
        Position exist = positionMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "岗位不存在");
        }
        if (positionMapper.countReferencedByEmployee(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该岗位下仍有关联员工，无法删除");
        }
        Position patch = new Position();
        patch.setId(id);
        patch.setStatus(0);
        positionMapper.updateById(patch);
        log.info("[ORG] 停用岗位 id={}", id);
    }

    private Company requireCompany(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公司不存在");
        }
        return company;
    }

    private Department requireDepartment(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return dept;
    }
}
