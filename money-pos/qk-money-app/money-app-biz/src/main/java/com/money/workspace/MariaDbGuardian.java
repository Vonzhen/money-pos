package com.money.workspace;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
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
            // 🌟 只用安全字符集生成密码，防止 SQL 注入或特殊字符报错
            dbPassword = "Mp_" + RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 12);
            isFirstRun = true;
        }
    }

    private static void bootEngine() {
        String mariadbPath = WorkspaceEnv.getAppHome() + "/mariadb";
        File mysqldExe = new File(mariadbPath + "/bin/mysqld.exe");
        File installDbExe = new File(mariadbPath + "/bin/mysql_install_db.exe");
        File dataDir = new File(mariadbPath + "/data");

        if (!mysqldExe.exists()) {
            throw new RuntimeException("❌ [Guardian] 致命错误：缺失数据库引擎文件!");
        }

        try {
            // 1. 内核初始化检测
            if (isFirstRun && (!dataDir.exists() || FileUtil.isEmpty(dataDir))) {
                log.info("⏳ [Guardian] 检测到全新安装，正在初始化数据库内核...");
                FileUtil.mkdir(dataDir);
                Process initProcess = new ProcessBuilder(installDbExe.getAbsolutePath(), "--datadir=" + dataDir.getAbsolutePath())
                        .redirectErrorStream(true).start();
                drainStream(initProcess.getInputStream()); // 🌟 必须消费日志流，防止阻塞死锁
                initProcess.waitFor();
            }

            // 2. 拉起数据库进程
            log.info("⚙️ [Guardian] 正在拉起 MariaDB 服务 (端口: {})...", DB_PORT);
            dbProcess = new ProcessBuilder(
                    mysqldExe.getAbsolutePath(),
                    "--port=" + DB_PORT,
                    "--datadir=" + dataDir.getAbsolutePath(),
                    "--character-set-server=utf8mb4"
            ).directory(new File(mariadbPath)).redirectErrorStream(true).start();

            // 🌟 核心：持续消费运行日志，防止缓冲区堆满导致进程假死
            drainStream(dbProcess.getInputStream());

            // 注册优雅停机钩子
            Runtime.getRuntime().addShutdownHook(new Thread(MariaDbGuardian::gracefulShutdown));

            // 3. 🌟 双层 JDBC 医疗级探活
            if (!waitForDatabaseReady()) {
                throw new RuntimeException("❌ [Guardian] 数据库启动超时或被异常占用！");
            }

        } catch (Exception e) {
            log.error("❌ [Guardian] 守护进程启动失败: ", e);
            System.exit(1); // 启动失败必须立即终止，绝不带病运行
        }
    }

    private static boolean waitForDatabaseReady() {
        System.out.print("📡 [Guardian] 正在建立 JDBC 底层心跳检测 ");
        String currentPwd = isFirstRun ? "" : dbPassword;
        String sysUrl = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false&serverTimezone=GMT%2B8";

        // 第一层：尝试连接 MySQL 系统库 (最多等 15 秒)
        boolean connected = false;
        for (int i = 0; i < 15; i++) {
            try (Connection conn = DriverManager.getConnection(sysUrl, "root", currentPwd)) {
                connected = true;

                // 🌟 第二层：如果是首次运行，执行改密并创建业务库！闭环验证！
                if (isFirstRun) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + dbPassword + "'");
                        stmt.execute("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                        FileUtil.writeString(dbPassword, new File(PWD_FILE), StandardCharsets.UTF_8);
                        log.info("\n🛡️ [Guardian] 首次安全加固完成！业务库已就绪。");
                    }
                }
                break;
            } catch (Exception e) {
                System.out.print(".");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println();
        return connected;
    }

    private static void gracefulShutdown() {
        log.info("\n🛑 [Guardian] 接收到系统关闭信号，正在安全落盘...");
        String url = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false&serverTimezone=GMT%2B8";
        try (Connection conn = DriverManager.getConnection(url, "root", dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN"); // 发送优雅停机指令

            // 🌟 宽容等待进程自行退出 (最多等 5 秒)
            if (dbProcess != null) {
                boolean exited = dbProcess.waitFor(5, TimeUnit.SECONDS);
                if (exited) log.info("✅ [Guardian] 数据库已安全关闭。");
                else throw new RuntimeException("Timeout");
            }
        } catch (Exception e) {
            log.warn("⚠️ [Guardian] 优雅停机超时，执行进程强制销毁...");
            if (dbProcess != null) dbProcess.destroyForcibly();
        }
    }

    // 持续消费进程日志流
    private static void drainStream(InputStream is) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                while (reader.readLine() != null) { /* 持续抛弃日志，防止系统管道阻塞 */ }
            } catch (Exception ignored) {}
        }).start();
    }
}