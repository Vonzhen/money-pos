package com.money.workspace;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppConfigInjector {

    public static void inject() {
        // 1. 注入动态数据库连接
        String dbUrl = String.format("jdbc:mysql://127.0.0.1:%d/%s?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%%2B8&createDatabaseIfNotExist=true",
                MariaDbGuardian.DB_PORT, MariaDbGuardian.DB_NAME);

        System.setProperty("spring.datasource.url", dbUrl);
        System.setProperty("spring.datasource.username", "root");
        System.setProperty("spring.datasource.password", MariaDbGuardian.getDbPassword());
        System.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");

        // 2. 注入动态物理存储路径
        System.setProperty("local.bucket", WorkspaceEnv.getAppHome() + "/assets/");
        System.setProperty("money.cache.local.provider", "hutool");

        // ==========================================
        // 🌟 3. Flyway 工业级数据库迁移与版本控制机制
        // ==========================================

        // 彻底禁用旧的 Spring SQL 初始化脚本机制
        //System.setProperty("spring.sql.init.mode", "never");

        // 激活 Flyway
        //System.setProperty("spring.flyway.enabled", "true");
        // 指定扫描的脚本目录 (刚好对应我们刚才建的文件夹)
        //System.setProperty("spring.flyway.locations", "classpath:db/migration");
        // 核心：当遇到已经有数据的旧库时，自动以当前状态作为基准线 (防老库报错报错)
        //System.setProperty("spring.flyway.baseline-on-migrate", "true");
        // 基准线版本设为 1.0.0 (如果原来有库，就默认它已经是 1.0.0 了)
        //System.setProperty("spring.flyway.baseline-version", "0");
        // 开发环境容错：允许 SQL 脚本被修改或乱序执行 (正式发版可关)
        //System.setProperty("spring.flyway.out-of-order", "true");

        //log.info("💉 [Injector] 运行时环境与 Flyway 版本控制策略注入完毕！将控制权移交 Spring Boot...");
    }
}