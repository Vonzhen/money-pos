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

        // ==========================================
        // 🌟 2. 注入动态物理存储路径 (核心修复：图片资产移入安全区)
        // ==========================================
        // 将上传的附件、图片全部指向持久化的 AppData 数据区，与程序区彻底解耦！
        System.setProperty("local.bucket", WorkspaceEnv.getAppData() + "/assets/");
        System.setProperty("money.cache.local.provider", "hutool");

        // 3. Flyway 等配置目前已在 application-money.yml 中托管，此处保持注释即可
        // ...

        log.info("💉 [Injector] 数据库挂载点与静态资源隧道 (Assets) 注入完毕！");
    }
}