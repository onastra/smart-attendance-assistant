package com.lianzhi.attendance.system.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.system.entity.Employee;
import com.lianzhi.attendance.system.entity.SysRole;
import com.lianzhi.attendance.system.entity.SysUser;
import com.lianzhi.attendance.system.mapper.EmployeeMapper;
import com.lianzhi.attendance.system.mapper.SysRoleMapper;
import com.lianzhi.attendance.system.mapper.SysUserMapper;
import com.lianzhi.attendance.system.query.EmployeeQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 员工档案。
 *
 * <p>约定：员工"离职"即物理删除——档案行与其关联数据（打卡流水、日统计、申请单、
 * 人脸特征、场所绑定、登录账号）在同一个事务里一并清除，不留离职状态的历史行；
 * 因而不存在"复职"操作，误删需重新建档。</p>
 *
 * <p>新建员工时在同一事务里自动开通登录账号：账号 = 工号，初始密码 = {@link #INITIAL_PASSWORD}，
 * 角色固定为普通员工（EMPLOYEE），员工可直接登录小程序打卡或登录 Web 端查看个人信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    /** 自动分配账号的统一初始密码，与种子数据 emp01~emp03 保持一致 */
    public static final String INITIAL_PASSWORD = "Emp@123";

    private static final String EMPLOYEE_ROLE_CODE = "EMPLOYEE";

    private final EmployeeMapper employeeMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
    public CreateResult create(Employee employee) {
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
        String username = createLoginAccount(employee);
        log.info("[EMP] 新建员工 id={} empNo={} name={} 账号={}",
                employee.getId(), empNo, employee.getName(),
                username != null ? username : "（自动分配失败，见日志）");
        return new CreateResult(employee.getId(), username,
                username != null ? INITIAL_PASSWORD : null);
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
        employeeMapper.deleteUserRolesByEmployeeId(id);
        employeeMapper.deleteUserByEmployeeId(id);
        employeeMapper.clearDeptLeaderByEmployeeId(id);
        employeeMapper.deleteById(id);
        log.info("[EMP] 离职删除员工 id={} empNo={} name={}（含关联数据与登录账号）",
                id, employee.getEmpNo(), employee.getName());
    }

    /**
     * 新建员工后自动开通登录账号：账号 = 工号，初始密码统一 {@link #INITIAL_PASSWORD}，角色 = 普通员工。
     *
     * <p>账号被占用（如同名历史账号）时降级为"只建档不开号"并记日志，
     * 不让整个建档事务回滚——账号可由管理员在「账号管理」手动补建。</p>
     */
    private String createLoginAccount(Employee employee) {
        if (sysUserMapper.countByUsername(employee.getEmpNo(), null) > 0) {
            log.warn("[EMP] 工号 {} 已被其他登录账号占用，员工 id={} 跳过自动分配账号",
                    employee.getEmpNo(), employee.getId());
            return null;
        }
        Long roleId = sysRoleMapper.selectAllRoles().stream()
                .filter(r -> EMPLOYEE_ROLE_CODE.equals(r.getRoleCode()))
                .map(SysRole::getId)
                .findFirst()
                .orElse(null);
        SysUser user = new SysUser();
        user.setUsername(employee.getEmpNo());
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setNickname(employee.getName());
        user.setEmployeeId(employee.getId());
        user.setStatus(1);
        sysUserMapper.insert(user);
        if (roleId != null) {
            sysUserMapper.insertUserRoles(user.getId(), List.of(roleId));
        }
        log.info("[EMP] 已自动分配登录账号 username={} userId={} roleId={}",
                user.getUsername(), user.getId(), roleId);
        return user.getUsername();
    }

    /** 建档结果：id 之外附带自动分配的登录账号与初始密码（明文仅此一次返回给创建者） */
    public record CreateResult(Long id, String username, String initialPassword) {
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
