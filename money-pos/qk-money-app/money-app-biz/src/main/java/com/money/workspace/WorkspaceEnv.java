package com.money.workspace;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class WorkspaceEnv {

    private static String APP_HOME;
    private static String APP_DATA; // 🌟 新增：持久化数据安全区

    /**
     * 获取真实的软件安装根目录 (用于存放变动的程序文件)
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
        APP_HOME = FileUtil.normalize(APP_HOME);
        return APP_HOME;
    }

    /**
     * 🌟 获取真实的持久化数据目录 (POS-Data)
     * 核心 3 层自我修复寻址策略
     */
    public static String getAppData() {
        if (APP_DATA != null) return APP_DATA;

        // 1. 第一层兜底：读取启动参数
        String argData = System.getProperty("app.data");
        if (StrUtil.isNotBlank(argData)) {
            APP_DATA = FileUtil.normalize(argData);
            return APP_DATA;
        }

        // 2. 第二层兜底：盲扫历史数据 (双重防伪校验)
        String[] drives = {"D:\\", "E:\\", "C:\\"};
        for (String drive : drives) {
            if (FileUtil.exist(drive)) {
                String testPath = drive + "WanXiang/POS-Data";
                File metaFile = new File(testPath, ".wx_meta");
                File ibdataFile = new File(testPath, "db_data/ibdata1");
                if (metaFile.exists() && ibdataFile.exists()) {
                    APP_DATA = FileUtil.normalize(testPath);
                    return APP_DATA;
                }
            }
        }

        // 3. 第三层兜底：全新分配安全区
        if (FileUtil.exist("D:\\")) {
            APP_DATA = FileUtil.normalize("D:\\WanXiang\\POS-Data");
        } else {
            APP_DATA = FileUtil.normalize("C:\\WanXiang\\POS-Data");
        }
        return APP_DATA;
    }

    public static void prepareDirectories() {
        String appHome = getAppHome();
        String appData = getAppData(); // 🌟 拿到安全区路径

        // 🌟 彻底分家：把图片、日志、备份 全部建在安全区里！
        FileUtil.mkdir(appData + File.separator + "assets");
        FileUtil.mkdir(appData + File.separator + "logs");
        FileUtil.mkdir(appData + File.separator + "backups");
        FileUtil.mkdir(appData + File.separator + "db_data"); // 给数据库备用

        log.info("⚙️ [Workspace] 程序核心锁定于: {}", appHome);
        log.info("🛡️ [Workspace] 数据资产锁定于: {}", appData);

        // 🌟 神来之笔：把安全区路径写入系统全局变量
        // 这样 Spring Boot 的各种配置就能直接通过 ${app.data} 拿到它了！
        System.setProperty("app.data", appData);
        System.setProperty("app.home", appHome);
    }
}