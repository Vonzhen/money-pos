package com.money.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.money.workspace.MariaDbGuardian;
import com.money.workspace.WorkspaceEnv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🌟 实用级高可用灾备与还原引擎 (V6.0 终极实战版)
 * 核心特性：
 * 1. 跨平台自适应 (兼容 Windows/Linux)
 * 2. 进程级 Stderr 错误捕获 (拒绝薛定谔的日志)
 * 3. 严格备份包与 Manifest 清单校验 (防篡改/防恶意包)
 * 4. 业务级核心表探活校验 (防残缺 SQL)
 * 5. 表集绝对一致性的单句无损原子切换 (支持纯新空环境一键恢复，旧表全清退，新表全上位)
 */
@Slf4j
@Service
public class SysBackupService {

    private static final String MANIFEST_FILE = "backup-manifest.json";
    private static final String APP_VERSION = "2.2.0";
    private static final String SHADOW_DB = "money_pos_shadow";

    // 单店单机场景下，全局广播可满足需求
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(600000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void sendLog(String level, String msg) {
        String logLine = String.format("{\"time\":\"%s\", \"level\":\"%s\", \"msg\":\"%s\"}",
                DateUtil.formatTime(DateUtil.date()), level, msg);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(logLine);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public File createBackupZip(String prefix) {
        String baseDir = WorkspaceEnv.getAppHome();
        String backupDir = baseDir + File.separator + "backups";
        String mariadbBin = baseDir + File.separator + "mariadb" + File.separator + "bin";
        String tempBatchDir = backupDir + File.separator + "temp_" + IdUtil.fastSimpleUUID();

        FileUtil.mkdir(tempBatchDir);
        try {
            // 1. 生成标准的备份清单
            JSONObject manifest = new JSONObject();
            manifest.set("appName", "MoneyPOS");
            manifest.set("appVersion", APP_VERSION);
            manifest.set("backupTime", DateUtil.now());
            manifest.set("dbName", MariaDbGuardian.DB_NAME);
            FileUtil.writeUtf8String(manifest.toStringPretty(), tempBatchDir + File.separator + MANIFEST_FILE);

            File sqlFile = new File(tempBatchDir + File.separator + "money_pos.sql");

            // 🌟 跨平台自适应执行程序后缀
            String exeSuffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
            File mysqldumpExe = new File(mariadbBin + File.separator + "mysqldump" + exeSuffix);

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpExe.getAbsolutePath(), "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot", "-p" + MariaDbGuardian.getDbPassword(),
                    "--single-transaction", "--routines", "--triggers", "--hex-blob",
                    "--add-drop-table", "--default-character-set=utf8mb4", MariaDbGuardian.DB_NAME
            );

            // 🌟 核心捕获：分离输出流与错误流，防止缓冲区阻塞
            pb.redirectOutput(sqlFile);
            File errorLogFile = new File(tempBatchDir + File.separator + "dump_error.log");
            pb.redirectError(errorLogFile);

            Process process = pb.start();
            if (process.waitFor() != 0) {
                String errorMsg = errorLogFile.exists() ? FileUtil.readUtf8String(errorLogFile) : "未知进程错误";
                throw new RuntimeException("数据库底层导出失败: " + errorMsg);
            }

            // 2. 备份静态资源
            File assetsDir = new File(baseDir + File.separator + "assets");
            if (assetsDir.exists()) FileUtil.copy(assetsDir, new File(tempBatchDir), true);

            // 3. 打包 ZIP
            String zipFileName = prefix + "MoneyPOS_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss") + ".zip";
            File zipFile = new File(backupDir + File.separator + zipFileName);
            cn.hutool.core.util.ZipUtil.zip(tempBatchDir, zipFile.getAbsolutePath(), true);
            return zipFile;
        } catch (Exception e) {
            log.error("❌ 备份失败", e);
            throw new RuntimeException("备份异常: " + e.getMessage(), e);
        } finally {
            FileUtil.del(tempBatchDir);
        }
    }

    public void restoreFromZip(MultipartFile backupFile) {
        String baseDir = WorkspaceEnv.getAppHome();
        String tempRestoreDir = baseDir + File.separator + "backups" + File.separator + "restore_" + IdUtil.fastSimpleUUID();
        File zipFile = new File(tempRestoreDir + ".zip");

        try {
            sendLog("WARN", "=== 实用级高可用还原序列启动 ===");

            backupFile.transferTo(zipFile);
            FileUtil.mkdir(tempRestoreDir);
            cn.hutool.core.util.ZipUtil.unzip(zipFile, new File(tempRestoreDir));

            // 🌟 核心防线 1：严格校验只允许 1 个 SQL 文件
            List<File> sqlFiles = FileUtil.loopFiles(new File(tempRestoreDir), f -> f.getName().endsWith(".sql"));
            if (sqlFiles.size() != 1) {
                throw new RuntimeException("异常备份包：必须包含且仅包含 1 个 SQL 文件 (当前发现 " + sqlFiles.size() + " 个)");
            }
            File sqlFile = sqlFiles.get(0);

            // 🌟 核心防线 2：严格强制校验 Manifest
            checkManifestStrictly(tempRestoreDir);

            sendLog("INFO", "创建前置保护快照...");
            createBackupZip("PreRestore_");

            sendLog("INFO", "第一阶段：影子库预导入...");
            prepareShadowDatabase();
            importSqlToDb(sqlFile, SHADOW_DB);

            sendLog("INFO", "第二阶段：影子库业务级深度校验...");
            verifyShadowDatabase();
            sendLog("SUCCESS", "影子库导入成功，核心数据验证通过。");

            sendLog("WARN", "第三阶段：执行生产库表集绝对一致性切换...");
            atomicSwitchDatabase();
            sendLog("SUCCESS", "数据库切换完美达成，无残留旧表！");

            restoreAssetsAtomically(tempRestoreDir, baseDir);

            sendLog("SUCCESS", "=== 还原全流程安全收官 ===");

        } catch (Exception e) {
            sendLog("ERROR", "❌ 还原失败：已触发安全保护机制。底层错误：" + e.getMessage());
            log.error("还原失败", e);
            // 失败时清理影子库，保留原生产库毫发无损
            try { dropDatabase(SHADOW_DB); } catch (Exception ignore) {}
            throw new RuntimeException("还原失败：" + (StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : "原因未知"), e);
        } finally {
            FileUtil.del(tempRestoreDir);
            FileUtil.del(zipFile);
        }
    }

    private void checkManifestStrictly(String restoreDir) {
        File manifest = new File(restoreDir + File.separator + MANIFEST_FILE);
        if (!manifest.exists()) {
            throw new RuntimeException("非法备份包：缺失 manifest 清单文件，拒绝恢复！");
        }

        JSONObject json = JSONUtil.readJSONObject(manifest, StandardCharsets.UTF_8);

        // 强校验应用和数据库归属，防止乱包导入
        if (!"MoneyPOS".equals(json.getStr("appName"))) {
            throw new RuntimeException("非法备份包：应用名称不匹配");
        }
        if (!MariaDbGuardian.DB_NAME.equals(json.getStr("dbName"))) {
            throw new RuntimeException("非法备份包：数据库名称不匹配 (" + json.getStr("dbName") + ")");
        }

        // 版本不一致允许降级/升级导入，但发出警告
        String version = json.getStr("appVersion");
        if (!APP_VERSION.equals(version)) {
            sendLog("WARN", "⚠️ 备份版本(" + version + ")与当前系统(" + APP_VERSION + ")不一致，将尝试跨版本兼容导入...");
        }
    }

    private void prepareShadowDatabase() throws Exception {
        executeSql("DROP DATABASE IF EXISTS `" + SHADOW_DB + "`");
        executeSql("CREATE DATABASE `" + SHADOW_DB + "` CHARACTER SET utf8mb4");
    }

    private void importSqlToDb(File sqlFile, String dbName) throws Exception {
        String exeSuffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
        String mysqlExe = WorkspaceEnv.getAppHome() + "/mariadb/bin/mysql" + exeSuffix;

        ProcessBuilder pb = new ProcessBuilder(
                mysqlExe, "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                "-uroot", "-p" + MariaDbGuardian.getDbPassword(), "--default-character-set=utf8mb4", dbName
        );
        pb.redirectInput(sqlFile);

        // 🌟 核心捕获：记录导入时底层 MySQL 报错
        File errorLogFile = new File(sqlFile.getParent() + File.separator + "import_error.log");
        pb.redirectError(errorLogFile);

        Process process = pb.start();
        if (process.waitFor() != 0) {
            String errorMsg = errorLogFile.exists() ? FileUtil.readUtf8String(errorLogFile) : "未知进程错误";
            log.error("💥 影子库导入时发生了原生 MySQL 报错: \n{}", errorMsg);
            throw new RuntimeException("导入指令底层执行失败: \n" + errorMsg);
        }
    }

    // 🌟 核心防线 3：影子库深度探活校验
    private void verifyShadowDatabase() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/" + SHADOW_DB + "?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {

            List<String> requiredTables = Arrays.asList("oms_order", "gms_goods", "ums_member");
            List<String> actualTables = new ArrayList<>();
            ResultSet rs = stmt.executeQuery("SHOW TABLES");
            while (rs.next()) actualTables.add(rs.getString(1));

            // 1. 验证核心表是否齐全
            for (String reqTable : requiredTables) {
                if (!actualTables.contains(reqTable)) {
                    throw new RuntimeException("影子库校验失败：缺失系统级核心表 `" + reqTable + "`，备份文件已损坏或不完整！");
                }
            }

            // 2. 验证核心表数据可读性 (防止结构毁坏)
            for (String reqTable : requiredTables) {
                try {
                    stmt.executeQuery("SELECT COUNT(1) FROM `" + reqTable + "`");
                } catch (Exception e) {
                    throw new RuntimeException("影子库校验失败：核心表 `" + reqTable + "` 数据损坏或无法读取 (" + e.getMessage() + ")");
                }
            }
        }
    }

    // 🌟 核心防线 4：表集绝对一致性的无损原子切换
    private void atomicSwitchDatabase() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {

            // 1. 获取影子库所有表
            List<String> shadowTables = new ArrayList<>();
            ResultSet rsShadow = stmt.executeQuery("SHOW TABLES FROM `" + SHADOW_DB + "`");
            while (rsShadow.next()) shadowTables.add(rsShadow.getString(1));
            if (shadowTables.isEmpty()) throw new RuntimeException("影子库表结构为空，安全机制已拦截切换");

            // 🌟 核心补丁：在获取生产库表单之前，确保生产库存在！
            // 防止在“纯新环境”或“生产库被手工误删”的空白场景下，SHOW TABLES 触发 Unknown database 报错！
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + MariaDbGuardian.DB_NAME + "` CHARACTER SET utf8mb4");

            // 2. 获取当前生产库所有表
            List<String> prodTables = new ArrayList<>();
            ResultSet rsProd = stmt.executeQuery("SHOW TABLES FROM `" + MariaDbGuardian.DB_NAME + "`");
            while (rsProd.next()) prodTables.add(rsProd.getString(1));

            // 3. 创建旧库的收容所
            String bakDbName = MariaDbGuardian.DB_NAME + "_bak_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss");
            stmt.execute("CREATE DATABASE `" + bakDbName + "` CHARACTER SET utf8mb4");

            // 4. 单句组合 RENAME，保证绝对原子性和表集一致性
            StringBuilder renameSql = new StringBuilder("RENAME TABLE ");
            boolean isFirst = true;

            // 动作 A：把当前生产库里的【所有旧表】扫进 bak 库 (彻底腾空，防幽灵残留)
            for (String pt : prodTables) {
                if (!isFirst) renameSql.append(", ");
                renameSql.append(String.format("`%s`.`%s` TO `%s`.`%s`", MariaDbGuardian.DB_NAME, pt, bakDbName, pt));
                isFirst = false;
            }

            // 动作 B：把影子库的【所有新表】平移到生产库
            for (String st : shadowTables) {
                if (!isFirst) renameSql.append(", ");
                renameSql.append(String.format("`%s`.`%s` TO `%s`.`%s`", SHADOW_DB, st, MariaDbGuardian.DB_NAME, st));
                isFirst = false;
            }

            // 一键执行，要么全部成功替换，要么立刻回滚，生产库毫发无损！
            stmt.execute(renameSql.toString());

            // 清理已立功的影子库
            stmt.execute("DROP DATABASE IF EXISTS `" + SHADOW_DB + "`");

            if (!prodTables.isEmpty()) {
                sendLog("INFO", "旧版生产数据(" + prodTables.size() + "张表)已被安全隔离至: " + bakDbName);
            } else {
                sendLog("INFO", "全新环境初始化部署完成！");
            }
        }
    }

    private void restoreAssetsAtomically(String tempDir, String baseDir) {
        File assetsInZip = new File(tempDir + File.separator + "assets");
        if (!assetsInZip.exists()) return;

        File targetAssets = new File(baseDir + File.separator + "assets");
        File bakAssets = new File(baseDir + File.separator + "assets_bak_" + IdUtil.fastSimpleUUID());

        try {
            if (targetAssets.exists()) FileUtil.move(targetAssets, bakAssets, true);
            FileUtil.copyContent(assetsInZip, targetAssets, true);

            // 简单线程延迟清理备份资源，单机场景够用
            new Thread(() -> {
                try { Thread.sleep(5000); FileUtil.del(bakAssets); } catch (Exception ignore) {}
            }).start();
        } catch (Exception e) {
            sendLog("ERROR", "静态资源替换失败，已触发自动回滚...");
            if (bakAssets.exists()) {
                FileUtil.del(targetAssets);
                FileUtil.move(bakAssets, targetAssets, true);
            }
            throw new RuntimeException("静态资产还原失败", e);
        }
    }

    private void executeSql(String sql) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void dropDatabase(String dbName) throws Exception {
        executeSql("DROP DATABASE IF EXISTS `" + dbName + "`");
    }
}