package com.lianzhi.attendance.attendance.controller;

import com.lianzhi.attendance.attendance.dto.RebuildResult;
import com.lianzhi.attendance.attendance.service.DailyStatService;
import com.lianzhi.attendance.attendance.service.DataScopeResolver;
import com.lianzhi.attendance.attendance.service.JobLogService;
import com.lianzhi.attendance.attendance.service.StatisticsService;
import com.lianzhi.attendance.common.result.R;
import com.lianzhi.attendance.framework.auth.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日汇总重算入口的单元测试。
 *
 * <p>这里守护的是"不带日期参数时到底重算了什么"。曾踩过的坑：
 * 无参数只重算今天，而首次部署后历史/演示流水都在过去若干天，
 * 结果是管理员点了重算、看板依旧全 0，现场会被读成"统计模块没做"。</p>
 */
@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;
    @Mock
    private DailyStatService dailyStatService;
    @Mock
    private JobLogService jobLogService;
    @Mock
    private DataScopeResolver dataScopeResolver;

    private StatisticsController controller;

    @BeforeEach
    void setUp() {
        controller = new StatisticsController(statisticsService, dailyStatService, jobLogService, dataScopeResolver);
    }

    @Test
    @DisplayName("不带任何日期参数时，按最早打卡流水到今天全量重算")
    void noDateParamRebuildsSinceEarliestRecord() {
        LocalDate earliest = LocalDate.of(2026, 10, 12);
        when(dailyStatService.earliestRecordDate()).thenReturn(earliest);
        when(dailyStatService.rebuildRange(eq(earliest), any()))
                .thenReturn(List.of(result("2026-10-12", 5)));

        R<Map<String, Object>> response = controller.rebuild(null, null, null);

        Map<String, Object> data = response.getData();
        verify(dailyStatService).rebuildRange(eq(earliest), eq(LocalDate.now()));
        verify(dailyStatService, never()).rebuild(any());
        assertEquals(5, data.get("affectedRows"));
        assertEquals(1, data.get("days"));
    }

    @Test
    @DisplayName("一条打卡记录都没有时，退化为只算今天且不报错")
    void noRecordAtAllFallsBackToToday() {
        when(dailyStatService.earliestRecordDate()).thenReturn(null);
        when(dailyStatService.rebuildRange(any(), any())).thenReturn(List.of());

        R<Map<String, Object>> response = controller.rebuild(null, null, null);

        verify(dailyStatService).rebuildRange(eq(LocalDate.now()), eq(LocalDate.now()));
        assertEquals(0, response.getData().get("affectedRows"));
    }

    @Test
    @DisplayName("显式指定单日时只重算那一天，不擅自扩大范围")
    void explicitSingleDateRebuildsOnlyThatDay() {
        LocalDate target = LocalDate.of(2026, 10, 13);
        when(dailyStatService.rebuild(target)).thenReturn(result("2026-10-13", 5));

        R<Map<String, Object>> response = controller.rebuild(target, null, null);

        verify(dailyStatService).rebuild(target);
        verify(dailyStatService, never()).rebuildRange(any(), any());
        assertEquals(5, response.getData().get("affectedRows"));
        assertEquals(1, response.getData().get("days"));
    }

    @Test
    @DisplayName("显式指定区间时按区间重算")
    void explicitRangeIsUsedAsIs() {
        LocalDate from = LocalDate.of(2026, 10, 12);
        LocalDate to = LocalDate.of(2026, 10, 25);
        when(dailyStatService.rebuildRange(from, to)).thenReturn(List.of(result("2026-10-12", 5)));

        controller.rebuild(null, from, to);

        verify(dailyStatService).rebuildRange(from, to);
        verify(dailyStatService, never()).earliestRecordDate();
    }

    private static RebuildResult result(String date, int rows) {
        RebuildResult result = new RebuildResult();
        result.setDate(date);
        result.setAffectedRows(rows);
        result.setEmployeeCount(rows);
        return result;
    }

    @Test
    @DisplayName("AuthUser 上下文不被本入口使用时的兜底：不因空用户崩溃")
    void unusedDependenciesDoNotBreakFlow() {
        when(dailyStatService.earliestRecordDate()).thenReturn(LocalDate.now().minusDays(3));
        when(dailyStatService.rebuildRange(any(), any())).thenReturn(List.of(result("2026-10-24", 2)));

        AuthUser user = new AuthUser();
        // 此处并未使用 user，仅为确保签名可选、调用不依赖上下文
        assertEquals(null, user.getUsername());
        assertEquals(0, controller.rebuild(null, null, null).getCode());
    }
}
