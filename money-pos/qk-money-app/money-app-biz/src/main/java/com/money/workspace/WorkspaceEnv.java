package com.money.workspace;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class WorkspaceEnv {

    private static String APP_HOME;

    /**
     * 🌟 获取真实的软件安装根目录
     */
    public static String getAppHome() {
        if (APP_HOME != null) return APP_HOME;

        try {
            String path = WorkspaceEnv.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File file = new File(path);

            if (file.isFile()) {
                APP_HOME = file.getParentFile().getAbsolutePath();
            } else {
                APP_HOME = file.getParentFile().getParentFile().getAbsolutePath();
            }
        } catch (Exception e) {
            APP_HOME = System.getProperty("user.dir");
        }
        // 标准化路径，避免 Windows 下的斜杠混乱
        APP_HOME = FileUtil.normalize(APP_HOME);
        return APP_HOME;
    }

    public static void prepareDirectories() {
        String base = getAppHome();
        FileUtil.mkdir(base + File.separator + "assets");
        FileUtil.mkdir(base + File.separator + "logs");
        FileUtil.mkdir(base + File.separator + "backups");
        log.info("📂 [Workspace] 运行环境已锁定: {}", base);
    }
}