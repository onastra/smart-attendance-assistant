package com.lianzhi.attendance.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描。
 *
 * <p>扫描根目录下所有模块的 <code>*.mapper</code> 包，业务模块无需各行定义扫描路径，
 * 新增模块只要遵守包命名即可被自动发现。</p>
 */
@Configuration
@MapperScan(basePackages = "com.lianzhi.attendance.**.mapper")
public class MyBatisScanConfig {
}
