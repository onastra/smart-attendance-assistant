package com.lianzhi.attendance.attendance.task;

import com.lianzhi.attendance.attendance.service.JobLogService;
import com.lianzhi.attendance.attendance.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 过期二维码清理：每 5 分钟把超时的动态码置为 EXPIRED。
 *
 * <p>为什么需要它而不是只靠打卡时的即时校验：
 * 打卡校验只能拦住"拿过期码来打卡"，但管理端列表如果不落状态，
 * 就会显示"状态 VALID 却已超时"的矛盾数据。这里让状态与事实一致。</p>
 *
 * <p>幂等：SQL 只命中 status='VALID' 且 expire_time &lt; now 的行，
 * 重复执行第二次影响行数为 0，不会把已失效的码反复计数。</p>
 *
 * <p>周期取 5 分钟而不是每分钟：动态码有效期 120 秒，延迟 5 分钟落状态不影响安全
 * （校验是实时的），但能避免无谓的高频 UPDATE。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrcodeExpireTask {

    private static final String JOB_NAME = "QRCODE_EXPIRE_CLEANUP";

    private final QrCodeService qrCodeService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void expireOutdated() {
        LocalDateTime start = LocalDateTime.now();
        try {
            int rows = qrCodeService.expireOutdated();
            jobLogService.log(JOB_NAME, start, LocalDateTime.now(), true, rows, "expired=" + rows);
        } catch (Exception e) {
            log.error("[JOB] 过期二维码清理失败", e);
            jobLogService.log(JOB_NAME, start, LocalDateTime.now(), false, 0, e.getMessage());
        }
    }
}
