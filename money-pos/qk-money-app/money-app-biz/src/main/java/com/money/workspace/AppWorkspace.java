package com.money.workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * MoneyPOS 系统级守护者 (V2.2 智能侧挂 + 自动初始化版)
 */
public class AppWorkspace {

    private static final String ROOT_PATH = "D:/MoneyPOS_Data";
    private static final int DB_PORT = 9102;

    // 🌟 核心新增：记录本次启动是否为首次安装
    private static boolean isFirstRun = false;

    public static void init() {
        System.out.println("=================================================");
        System.out.println("[MoneyPOS] 正在启动 V2.2 工业级侧挂引擎...");

        new File(ROOT_PATH + "/assets").mkdirs();
        new File(ROOT_PATH + "/logs").mkdirs();

        startMariaDB();
        injectEnvironmentVariables();

        System.out.println("=================================================");
    }

    private static void startMariaDB() {
        String mariadbPath = ROOT_PATH + "/mariadb";
        File mysqldExe = new File(mariadbPath + "/bin/mysqld.exe");
        File installDbExe = new File(mariadbPath + "/bin/mysql_install_db.exe");
        File dataDir = new File(mariadbPath + "/data");

        if (!mysqldExe.exists()) {
            System.err.println("[MoneyPOS 致命错误] 找不到数据库引擎: " + mysqldExe.getAbsolutePath());
            return;
        }

        if (isPortInUse(DB_PORT)) {
            System.out.println("[MoneyPOS] 数据库已在端口 " + DB_PORT + " 运行。");
            return;
        }

        try {
            // 【核心新增】：自动初始化数据库系统文件
            if (!dataDir.exists() || dataDir.list() == null || dataDir.list().length == 0) {
                System.out.println("[MoneyPOS] 首次运行检测！正在自动初始化 MariaDB 系统内核...");

                isFirstRun = true; // 🌟 标记：这是该电脑第一次运行 MoneyPOS

                dataDir.mkdirs();
                ProcessBuilder initPb = new ProcessBuilder(
                        installDbExe.getAbsolutePath(),
                        "--datadir=" + dataDir.getAbsolutePath()
                );
                initPb.redirectErrorStream(true);
                Process initProcess = initPb.start();

                // 打印初始化日志
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(initProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[MariaDB 初始化] " + line);
                    }
                }
                initProcess.waitFor();
                System.out.println("[MoneyPOS] 数据库系统内核初始化完成！");
            }

            System.out.println("[MoneyPOS] 正在唤醒 MariaDB 数据库服务...");
            ProcessBuilder pb = new ProcessBuilder(
                    mysqldExe.getAbsolutePath(),
                    "--port=" + DB_PORT,
                    "--datadir=" + dataDir.getAbsolutePath(),
                    "--character-set-server=utf8mb4"
            );

            pb.directory(new File(mariadbPath));
            pb.redirectErrorStream(true);
            Process dbProcess = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(dbProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // System.out.println("[MariaDB 运行日志] " + line); // 可选关闭日志刷屏
                    }
                } catch (Exception ignored) {}
            }).start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[MoneyPOS] 软件关闭，正在安全关闭数据库...");
                dbProcess.destroy();
            }));

            System.out.print("[MoneyPOS] 等待数据库就绪 ");
            boolean isUp = false;
            for (int i = 0; i < 30; i++) {
                if (isPortInUse(DB_PORT)) {
                    isUp = true;
                    break;
                }
                System.out.print(".");
                Thread.sleep(500);
            }
            System.out.println();

            if (isUp) {
                System.out.println("[MoneyPOS] 数据库服务唤醒成功！");
            } else {
                System.err.println("[MoneyPOS] 数据库启动超时！");
            }

        } catch (Exception e) {
            System.err.println("[MoneyPOS] 数据库操作失败: " + e.getMessage());
        }
    }

    private static void injectEnvironmentVariables() {
        String jdbcUrl = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/money_pos?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8&createDatabaseIfNotExist=true";
        System.setProperty("spring.datasource.url", jdbcUrl);
        System.setProperty("spring.datasource.username", "root");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");

        // 🌟 核心修复：如果是首次运行才执行 SQL，否则永远不执行 (保护您的数据！)
        if (isFirstRun) {
            System.setProperty("spring.sql.init.mode", "always");
        } else {
            System.setProperty("spring.sql.init.mode", "never");
        }

        System.setProperty("spring.sql.init.schema-locations", "classpath:schema.sql");
        System.setProperty("money.cache.local.provider", "hutool");
        System.setProperty("spring.redis.host", "127.0.0.1");
        System.setProperty("local.bucket", ROOT_PATH + "/assets/");
    }

    private static boolean isPortInUse(int port) {
        try (Socket ignored = new Socket("127.0.0.1", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}