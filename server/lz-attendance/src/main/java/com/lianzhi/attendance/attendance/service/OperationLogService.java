package com.lianzhi.attendance.attendance.service;

import com.lianzhi.attendance.attendance.mapper.AttSupportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志。
 *
 * <p>为什么单独开事务：打卡失败时主事务会回滚，如果日志写在同一个事务里，
 * "有人用伪造二维码打过卡""某员工反复在非本人场所打卡"这类关键留痕会一起被回滚掉，
 * 恰恰是最该留下的记录反而消失了。因此这里用 REQUIRES_NEW 独立提交。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final AttSupportMapper supportMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String module, String action, String targetId,
                    String ip, boolean success, String message) {
        try {
            supportMapper.insertOperationLog(userId, module, action, targetId, ip,
                    success ? 1 : 0, truncate(message));
        } catch (Exception e) {
            // 日志写入失败不能影响主流程，但必须显式暴露出来
            log.error("[LOG] 操作日志写入失败 module={} action={}", module, action, e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
