package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.mapper.AttStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志。
 *
 * <p>与 {@link OperationLogService} 同理单独开事务：汇总重算失败回滚时，
 * "这次重算到底跑没跑、跑了多久、影响了几行"必须留下来，
 * 否则排查"今天的看板数据为什么是空的"时没有任何凭据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogService {

    private final AttStatMapper statMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String jobName, LocalDateTime startTime, LocalDateTime endTime,
                    boolean success, Integer affectedRows, String message) {
        try {
            statMapper.insertJobLog(jobName, startTime, endTime, success ? 1 : 0,
                    affectedRows, truncate(message));
        } catch (Exception e) {
            log.error("[JOB] 任务日志写入失败 job={}", jobName, e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
