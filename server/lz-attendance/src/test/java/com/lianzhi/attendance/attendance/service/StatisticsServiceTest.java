package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.dto.StatSummary;
import com.lianzhi.attendance.attendance.mapper.StatisticsMapper;
import com.lianzhi.attendance.attendance.query.StatisticsQuery;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.framework.auth.AuthUser;
import com.lianzhi.attendance.framework.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统计概览服务的单元测试，重点是缓存键与数据范围。
 *
 * <p>缓存这类"性能优化"最容易引入的是越权：管理员查过的全量结果被缓存后，
 * 若键里没有数据范围，部门主管下一次查询会直接命中管理员视角的数字。
 * 因此这里把"不同范围必须落在不同键上"当作安全断言来写，而不是性能测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private StatisticsMapper statisticsMapper;
    @Mock
    private DataScopeResolver dataScopeResolver;
    @Mock
    private CacheService cacheService;

    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(statisticsMapper, dataScopeResolver, cacheService);
        ReflectionTestUtils.setField(service, "summaryTtlSeconds", 60L);
    }

    @Test
    @DisplayName("不同数据范围必须落在不同缓存键上，否则部门主管会命中管理员的全量缓存")
    void cacheKeyMustDifferByDataScope() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));

        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);
        service.summary(query("2026-10-13", "2026-10-26"), user(1L, Set.of("system:user:list")));

        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_DEPT);
        when(dataScopeResolver.deptIdOf(any())).thenReturn(10L);
        service.summary(query("2026-10-13", "2026-10-26"), user(2L, Set.of()));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2)).put(keys.capture(), any(StatSummary.class), any(Duration.class));

        List<String> captured = keys.getAllValues();
        assertNotEquals(captured.get(0), captured.get(1), "两种数据范围写进了同一个缓存键，存在越权风险");
        assertTrue(captured.get(1).contains("dept=10"), "部门范围的键必须带上部门 ID，实际：" + captured.get(1));
        assertTrue(captured.get(0).contains("dept=null"), "管理员范围的键应明确 dept 为空，实际：" + captured.get(0));
    }

    @Test
    @DisplayName("缓存命中时如实标记 cached=true，且不再回查数据库")
    void cacheHitIsMarkedAndSkipsDatabase() {
        StatSummary cached = sample(0);
        cached.setCached(false);
        cached.setGeneratedAt("2026-10-26 20:00:00");
        when(cacheService.get(any(), eq(StatSummary.class))).thenReturn(cached);

        StatSummary result = service.summary(query("2026-10-13", "2026-10-26"), adminUser());

        assertTrue(result.isCached(), "命中缓存必须如实标记，不能让用户以为拿到了实时数据");
        assertEquals("2026-10-26 20:00:00", result.getGeneratedAt(), "生成时间应保留首次计算的时间");
        verify(statisticsMapper, never()).selectSummary(any());
        verify(cacheService, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("缓存未命中时计算、标记 cached=false 并写回缓存")
    void cacheMissComputesAndStores() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        StatSummary result = service.summary(query("2026-10-13", "2026-10-26"), adminUser());

        assertFalse(result.isCached());
        assertNotNull(result.getGeneratedAt(), "未命中时必须给出生成时间");
        verify(cacheService).put(any(), any(StatSummary.class), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("出勤率由工作日人天计算，休息日不计入分母")
    void attendanceRateExcludesRestDays() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        // 10 人天中有 2 天休息，正常 6 + 迟到 1 + 早退 1 = 8，出勤率 100%
        StatSummary result = service.summary(query("2026-10-13", "2026-10-26"), adminUser());

        assertEquals(8, result.getWorkdayPersonDays());
        assertEquals(100.0, result.getAttendanceRate(), 0.001);
    }

    @Test
    @DisplayName("范围内掺了部分演示补录数据时标注 mixed，不得当成真实考勤呈现")
    void partiallySampleDataIsMarkedAsMixed() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(5));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        StatSummary result = service.summary(query("2026-10-13", "2026-10-26"), adminUser());

        assertEquals("mixed", result.getDataSource());
        assertTrue(result.getDataSourceNote().contains("演示"), "备注里必须点明含演示数据，实际：" + result.getDataSourceNote());
    }

    @Test
    @DisplayName("范围内全是演示补录数据时标注 sample，比 mixed 更严厉")
    void fullySampleDataIsMarkedAsSample() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(10));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        StatSummary result = service.summary(query("2026-10-13", "2026-10-26"), adminUser());

        assertEquals("sample", result.getDataSource(), "演示人天覆盖全部人天时必须明确标注为 sample");
        assertTrue(result.getDataSourceNote().contains("不代表真实考勤"), "实际：" + result.getDataSourceNote());
    }

    @Test
    @DisplayName("全部为真实数据时标注 real，不做无谓的免责声明")
    void realDataIsMarkedAsReal() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        assertEquals("real", service.summary(query("2026-10-13", "2026-10-26"), adminUser()).getDataSource());
    }

    @Test
    @DisplayName("部门主管的查询条件被服务端强制收敛，前端传什么都不影响")
    void deptScopeIsNarrowedOnServerSide() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_DEPT);
        when(dataScopeResolver.deptIdOf(any())).thenReturn(10L);

        StatisticsQuery query = query("2026-10-13", "2026-10-26");
        query.setDeptId(999L); // 试图看别的部门
        service.summary(query, user(2L, Set.of()));

        assertEquals(10L, query.getDeptId(), "服务端必须覆盖调用方传入的部门，而不是照单全收");
    }

    @Test
    @DisplayName("普通员工的范围收敛到本人")
    void selfScopeIsNarrowedToSelf() {
        when(statisticsMapper.selectSummary(any())).thenReturn(sample(0));
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_SELF);
        when(dataScopeResolver.selfEmployeeId(any())).thenReturn(77L);

        StatisticsQuery query = query("2026-10-13", "2026-10-26");
        service.summary(query, user(3L, Set.of()));

        assertEquals(77L, query.getEmployeeId());
    }

    @Test
    @DisplayName("开始日期晚于结束日期：明确报错而不是返回空结果")
    void reversedDateRangeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.summary(query("2026-10-26", "2026-10-13"), adminUser()));

        assertTrue(ex.getMessage().contains("开始日期不能晚于结束日期"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("趋势粒度只接受白名单取值，非法值拒绝")
    void trendTypeIsWhitelisted() {
        StatisticsQuery query = query("2026-10-13", "2026-10-26");
        query.setType("century");
        when(dataScopeResolver.resolve(any())).thenReturn(DataScopeResolver.SCOPE_ALL);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.trend(query, adminUser()));
        assertTrue(ex.getMessage().contains("day / week / month"), "实际：" + ex.getMessage());
    }

    // ---------- 辅助方法 ----------

    private static StatisticsQuery query(String from, String to) {
        StatisticsQuery query = new StatisticsQuery();
        query.setDateFrom(from);
        query.setDateTo(to);
        return query;
    }

    private static AuthUser adminUser() {
        return user(1L, Set.of("system:user:list"));
    }

    private static AuthUser user(Long userId, Set<String> permissions) {
        AuthUser user = new AuthUser();
        user.setUserId(userId);
        user.setPermissions(permissions);
        return user;
    }

    /** personDays=10，其中 rest=2；normal=6 late=1 early=1 → 工作日人天 8，出勤 8 */
    private static StatSummary sample(long sampleDays) {
        StatSummary summary = new StatSummary();
        summary.setPersonDays(10);
        summary.setRestDays(2);
        summary.setNormalDays(6);
        summary.setLateDays(1);
        summary.setEarlyDays(1);
        summary.setAbsentDays(0);
        summary.setSampleDays(sampleDays);
        return summary;
    }
}
