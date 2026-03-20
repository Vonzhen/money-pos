package com.money.workspace;

/**
 * MoneyPOS 系统级守护者门面 (V3.0 工业级底座)
 */
public class AppWorkspace {

    public static void init() {
        System.out.println("=================================================");
        System.out.println("🚀 [MoneyPOS] 正在启动 V3.0 工业级侧挂引擎...");

        // 1. 锁定并构建物理运行环境
        WorkspaceEnv.prepareDirectories();

        // 2. 唤醒并守护数据库生命周期
        MariaDbGuardian.start();

        // 3. 将底层参数注入业务框架
        AppConfigInjector.inject();

        System.out.println("=================================================");
    }
}