package com.money.workspace;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class WorkspaceEnv {

    private static String APP_HOME;

    /**
     * 🌟 获取真实的软件安装根目录 (免疫 Electron 和 快捷方式的路径偏移)
     */
    public static String getAppHome() {
        if (APP_HOME != null) return APP_HOME;

        try {
            // 获取当前运行类所在的物理路径
            String path = WorkspaceEnv.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File file = new File(path);

            // 如果是运行的 Jar 包或 EXE，取其所在目录
            if (file.isFile()) {
                APP_HOME = file.getParentFile().getAbsolutePath();
            } else {
                // 如果是在 IDEA 里运行 (target/classes)，退回项目根目录
                APP_HOME = file.getParentFile().getParentFile().getAbsolutePath();
            }
        } catch (Exception e) {
            log.warn("[Workspace] 路径解析降级，使用备用路径...");
            APP_HOME = System.getProperty("user.dir");
        }
        return APP_HOME;
    }

    /**
     * 🌟 初始化并校验所有必须的物理目录
     */
    public static void prepareDirectories() {
        String base = getAppHome();
        FileUtil.mkdir(base + File.separator + "assets");
        FileUtil.mkdir(base + File.separator + "logs");
        FileUtil.mkdir(base + File.separator + "backups");
        log.info("📂 [Workspace] 运行环境已锁定: {}", base);
    }
}