package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.system.entity.Employee;
import com.lianzhi.attendance.system.mapper.EmployeeMapper;
import com.lianzhi.attendance.system.query.EmployeeQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 员工档案。
 *
 * <p>约定：员工"离职"即物理删除——档案行与其关联数据（打卡流水、日统计、申请单、
 * 人脸特征、场所绑定）在同一个事务里一并清除，不留离职状态的历史行；
 * 因而不存在"复职"操作，误删需重新建档。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    public PageResult<Employee> page(EmployeeQuery query) {
        long total = employeeMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, employeeMapper.selectByQuery(query));
    }

    public Employee detail(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "员工不存在");
        }
        return employee;
    }

    @Transactional
    public Long create(Employee employee) {
        String empNo = StringUtils.hasText(employee.getEmpNo())
                ? employee.getEmpNo()
                : generateEmpNo();
        if (employeeMapper.countByEmpNo(empNo, null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "工号 " + empNo + " 已存在");
        }
        employee.setEmpNo(empNo);
        if (employee.getStatus() == null) {
            employee.setStatus(1);
        }
        if (employee.getGender() == null) {
            employee.setGender(0);
        }
        employeeMapper.insert(employee);
        log.info("[EMP] 新建员工 id={} empNo={} name={}", employee.getId(), empNo, employee.getName());
        return employee.getId();
    }

    @Transactional
    public void update(Employee employee) {
        requireEmployee(employee.getId());
        if (StringUtils.hasText(employee.getEmpNo())
                && employeeMapper.countByEmpNo(employee.getEmpNo(), employee.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "工号已被其他员工占用");
        }
        employeeMapper.updateById(employee);
        log.info("[EMP] 编辑员工 id={}", employee.getId());
    }

    /**
     * 离职 = 物理删除。同一个事务里先清关联数据再删档案行，
     * 全库无外键约束，必须手动清理，否则留下孤儿行污染统计。
     */
    @Transactional
    public void delete(Long id) {
        Employee employee = requireEmployee(id);
        employeeMapper.deleteFaceByEmployeeId(id);
        employeeMapper.deleteApplicationByEmployeeId(id);
        employeeMapper.deleteDailyStatByEmployeeId(id);
        employeeMapper.deleteAttRecordByEmployeeId(id);
        employeeMapper.deleteEmployeePlaceByEmployeeId(id);
        employeeMapper.unlinkUserByEmployeeId(id);
        employeeMapper.clearDeptLeaderByEmployeeId(id);
        employeeMapper.deleteById(id);
        log.info("[EMP] 离职删除员工 id={} empNo={} name={}（含关联数据）",
                id, employee.getEmpNo(), employee.getName());
    }

    /**
     * 生成工号：EMP + yyyyMMdd + 四位随机数。
     *
     * <p>没有做"按日递增序列"，因为这需要额外的计数表或行锁；
     * 随机号配合唯一索引冲突重试足以满足演示规模，且逻辑更好解释。</p>
     */
    private String generateEmpNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String candidate = "EMP" + datePart + suffix;
        if (employeeMapper.countByEmpNo(candidate, null) > 0) {
            candidate = "EMP" + datePart + ThreadLocalRandom.current().nextInt(1000, 10000);
        }
        return candidate;
    }

    private Employee requireEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "员工不存在");
        }
        return employee;
    }
}
