package com.money;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MoneyPOS 系统主启动类
 * 负责引导 Spring Boot 应用及初始化单机版底层环境变量
 */
@SpringBootApplication
public class QkMoneyApplication {

    public static void main(String[] args) {
        // 1. 激活工作空间探测器（必须在 Spring 启动前执行）
        // 作用：锁定非 C 盘数据目录，并向系统动态注入 H2 数据库和素材存储的本地环境变量
        com.money.workspace.AppWorkspace.init();

        // 2. 执行 Spring Boot 标准启动逻辑
        SpringApplication.run(QkMoneyApplication.class, args);
    }

}