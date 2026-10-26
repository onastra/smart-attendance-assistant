package com.lianzhi.attendance.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务。
 *
 * <p>单独一个配置类而不是直接标在启动类上，是为了让"系统里有定时任务是件被显式声明的事"
 * ——阶段 4 的日汇总、阶段 6 的过期二维码清理都依赖它，关掉这个类即可一次性停掉所有任务
 * （演示或压测时有用）。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
