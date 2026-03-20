package com.money.workspace;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MariaDbGuardian {

    public static final int DB_PORT = 9102;
    public static final String DB_NAME = "money_pos";
    private static final String PWD_FILE = WorkspaceEnv.getAppHome() + File.separator + "mariadb" + File.separator + ".sys_secret.key";

    private static String dbPassword = "";
    private static boolean isFirstRun = false;
    private static Process dbProcess = null;

    public static String getDbPassword() { return dbPassword; }

    public static void start() {
        prepareSecretKey();
        bootEngine();
    }

    private static void prepareSecretKey() {
        File pwdFile = new File(PWD_FILE);
        if (pwdFile.exists()) {
            dbPassword = FileUtil.readString(pwdFile, StandardCharsets.UTF_8).trim();
            isFirstRun = false;
        } else {
            dbPassword = "Mp_" + RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 12);
            isFirstRun = true;
        }
    }

    private static void bootEngine() {
        String mariadbPath = WorkspaceEnv.getAppHome() + "/mariadb";
        File mysqldExe = new File(mariadbPath + "/bin/mysqld.exe");
        File installDbExe = new File(mariadbPath + "/bin/mysql_install_db.exe");
        File dataDir = new File(mariadbPath + "/data");

        // 🌟 核心：路径标准化，用于后续“主权校验”比对
        String expectedDataDir = FileUtil.normalize(dataDir.getAbsolutePath());

        if (!mysqldExe.exists()) {
            throw new RuntimeException("❌ [Guardian] 致命错误：缺失数据库引擎文件!");
        }

        // 🌟 1. 启动前“主权探测”
        if (isPortInUse(DB_PORT)) {
            log.warn("⚠️ [Guardian] 检测到端口 {} 已被占用，启动血缘鉴定...", DB_PORT);
            if (verifyInstanceIdentity(expectedDataDir)) {
                log.info("✅ [Guardian] 鉴定成功：该实例为本程序关联实例，直接复用。");
                return; // 已在运行，直接复用
            } else {
                throw new RuntimeException("❌ [Guardian] 端口 " + DB_PORT + " 被非关联数据库占用！为保护数据安全，程序已拦截启动。请关闭其他 MySQL/MariaDB 实例。");
            }
        }

        try {
            if (isFirstRun && (!dataDir.exists() || FileUtil.isEmpty(dataDir))) {
                log.info("⏳ [Guardian] 正在初始化数据库内核...");
                FileUtil.mkdir(dataDir);
                Process initProcess = new ProcessBuilder(installDbExe.getAbsolutePath(), "--datadir=" + dataDir.getAbsolutePath())
                        .redirectErrorStream(true).start();
                drainStream(initProcess.getInputStream());
                initProcess.waitFor();
            }

            log.info("⚙️ [Guardian] 正在拉起私有 MariaDB 服务...");
            dbProcess = new ProcessBuilder(
                    mysqldExe.getAbsolutePath(),
                    "--port=" + DB_PORT,
                    "--datadir=" + dataDir.getAbsolutePath(),
                    "--character-set-server=utf8mb4"
            ).directory(new File(mariadbPath)).redirectErrorStream(true).start();

            drainStream(dbProcess.getInputStream());
            Runtime.getRuntime().addShutdownHook(new Thread(MariaDbGuardian::gracefulShutdown));

            if (!waitForDatabaseReady(expectedDataDir)) {
                throw new RuntimeException("❌ [Guardian] 数据库就绪校验失败！");
            }

        } catch (Exception e) {
            log.error("❌ [Guardian] 守护进程启动失败: ", e);
            System.exit(1);
        }
    }

    /**
     * 🌟 核心：血缘鉴定 (身份核验)
     * 通过 SQL 查询验证当前端口后的数据库是否指向我们的安装目录
     */
    private static boolean verifyInstanceIdentity(String expectedDir) {
        String currentPwd = isFirstRun ? "" : dbPassword;
        String url = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", currentPwd);
             Statement stmt = conn.createStatement()) {

            // 1. 校验数据目录 (@@datadir)
            ResultSet rs = stmt.executeQuery("SELECT @@datadir");
            if (rs.next()) {
                String actualDir = FileUtil.normalize(rs.getString(1));
                // 数据库路径最后通常带 / 或 \，需要去除后比对
                if (!StrUtil.removeSuffix(actualDir, "/").equalsIgnoreCase(StrUtil.removeSuffix(expectedDir, "/")) &&
                        !StrUtil.removeSuffix(actualDir, "\\").equalsIgnoreCase(StrUtil.removeSuffix(expectedDir, "\\"))) {
                    log.error("❌ [Guardian] 数据目录不匹配! 期望: {}, 实际: {}", expectedDir, actualDir);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("❌ [Guardian] 无法连接到正在运行的实例进行身份核验: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isPortInUse(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean waitForDatabaseReady(String expectedDir) {
        log.info("📡 [Guardian] 正在验证实例就绪状态...");
        String currentPwd = isFirstRun ? "" : dbPassword;
        String sysUrl = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false&serverTimezone=GMT%2B8";

        for (int i = 0; i < 15; i++) {
            try (Connection conn = DriverManager.getConnection(sysUrl, "root", currentPwd)) {
                // 再次执行身份核验
                if (!verifyInstanceIdentity(expectedDir)) return false;

                if (isFirstRun) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + dbPassword + "'");
                        stmt.execute("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` CHARACTER SET utf8mb4");
                        // 🌟 注入主权签名表，防止未来误连其他同路径库（如拷贝安装目录后）
                        stmt.execute("CREATE TABLE IF NOT EXISTS `" + DB_NAME + "`.`sys_app_signature` (`id` INT PRIMARY KEY, `sign` VARCHAR(50))");
                        stmt.execute("INSERT IGNORE INTO `" + DB_NAME + "`.`sys_app_signature` VALUES (1, 'MoneyPOS')");

                        FileUtil.writeString(dbPassword, new File(PWD_FILE), StandardCharsets.UTF_8);
                        log.info("🛡️ [Guardian] 安全加固与签名注入完成。");
                    }
                }
                return true;
            } catch (Exception e) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    private static void gracefulShutdown() {
        log.info("\n🛑 [Guardian] 正在执行优雅停机...");
        String url = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
            if (dbProcess != null) dbProcess.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (dbProcess != null) dbProcess.destroyForcibly();
        }
    }

    private static void drainStream(InputStream is) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                while (reader.readLine() != null) {}
            } catch (Exception ignored) {}
        }).start();
    }
}