package com.lianzhi.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 联智考勤系统启动入口（模块化单体：所有业务模块以依赖形式聚合到本模块）。
 */
@SpringBootApplication(scanBasePackages = "com.lianzhi.attendance")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
