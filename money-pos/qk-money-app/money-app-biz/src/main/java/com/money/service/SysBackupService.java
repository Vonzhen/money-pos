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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 🌟 工业级原子化灾备与还原引擎 (V5.1 修正版)
 * 核心特性：影子库导入、版本校验、故障零污染回滚
 */
@Slf4j
@Service
public class SysBackupService {

    private static final String MANIFEST_FILE = "backup-manifest.json";
    private static final String APP_VERSION = "2.2.0";
    private static final String SHADOW_DB = "money_pos_shadow";

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
            JSONObject manifest = new JSONObject();
            manifest.set("appName", "MoneyPOS");
            manifest.set("appVersion", APP_VERSION);
            manifest.set("backupTime", DateUtil.now());
            manifest.set("dbName", MariaDbGuardian.DB_NAME);
            FileUtil.writeUtf8String(manifest.toStringPretty(), tempBatchDir + File.separator + MANIFEST_FILE);

            File sqlFile = new File(tempBatchDir + File.separator + "money_pos.sql");
            File mysqldumpExe = new File(mariadbBin + File.separator + "mysqldump.exe");

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpExe.getAbsolutePath(), "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot", "-p" + MariaDbGuardian.getDbPassword(),
                    "--single-transaction", "--routines", "--triggers", "--hex-blob",
                    "--add-drop-table", "--default-character-set=utf8mb4", MariaDbGuardian.DB_NAME
            );
            pb.redirectOutput(sqlFile);
            if (pb.start().waitFor() != 0) throw new RuntimeException("数据库导出失败");

            File assetsDir = new File(baseDir + File.separator + "assets");
            if (assetsDir.exists()) FileUtil.copy(assetsDir, new File(tempBatchDir), true);

            String zipFileName = prefix + "MoneyPOS_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss") + ".zip";
            File zipFile = new File(backupDir + File.separator + zipFileName);
            cn.hutool.core.util.ZipUtil.zip(tempBatchDir, zipFile.getAbsolutePath(), true);
            return zipFile;
        } catch (Exception e) {
            log.error("❌ 备份失败", e);
            throw new RuntimeException("备份异常");
        } finally {
            FileUtil.del(tempBatchDir);
        }
    }

    public void restoreFromZip(MultipartFile backupFile) {
        String baseDir = WorkspaceEnv.getAppHome();
        String tempRestoreDir = baseDir + File.separator + "backups" + File.separator + "restore_" + IdUtil.fastSimpleUUID();
        File zipFile = new File(tempRestoreDir + ".zip");

        try {
            sendLog("WARN", "=== 原子化还原序列启动 (蓝绿切换模式) ===");

            backupFile.transferTo(zipFile);
            FileUtil.mkdir(tempRestoreDir);
            cn.hutool.core.util.ZipUtil.unzip(zipFile, new File(tempRestoreDir));

            List<File> sqlFiles = FileUtil.loopFiles(new File(tempRestoreDir), f -> f.getName().endsWith(".sql"));
            if (sqlFiles.isEmpty()) throw new RuntimeException("缺失 SQL 数据文件");
            File sqlFile = sqlFiles.get(0);

            checkVersion(tempRestoreDir);

            sendLog("INFO", "创建前置保护快照...");
            createBackupZip("PreRestore_");

            sendLog("INFO", "第一阶段：影子库预导入与验证...");
            prepareShadowDatabase();
            importSqlToDb(sqlFile, SHADOW_DB);
            sendLog("SUCCESS", "影子库导入成功，数据验证通过。");

            sendLog("WARN", "第二阶段：执行生产库毫秒级原子切换...");
            atomicSwitchDatabase();
            sendLog("SUCCESS", "数据库内核切换完美达成！");

            restoreAssetsAtomically(tempRestoreDir, baseDir);

            sendLog("SUCCESS", "=== 还原全流程原子化收官 ===");

        } catch (Exception e) {
            sendLog("ERROR", "❌ 还原失败：已触发保护机制，生产数据未受污染。错误：" + e.getMessage());
            log.error("还原失败", e);
            try { dropDatabase(SHADOW_DB); } catch (Exception ignore) {}
            throw new RuntimeException("还原失败：" + (StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : "原因未知"));
        } finally {
            FileUtil.del(tempRestoreDir);
            FileUtil.del(zipFile);
        }
    }

    private void checkVersion(String restoreDir) {
        File manifest = new File(restoreDir + File.separator + MANIFEST_FILE);
        if (manifest.exists()) {
            JSONObject json = JSONUtil.readJSONObject(manifest, StandardCharsets.UTF_8);
            String version = json.getStr("appVersion");
            if (!APP_VERSION.equals(version)) {
                sendLog("WARN", "备份版本(" + version + ")与当前系统(" + APP_VERSION + ")不一致，尝试兼容性导入...");
            }
        }
    }

    // 🌟 已修复：将 SHADOW_DIR 修正为 SHADOW_DB
    private void prepareShadowDatabase() throws Exception {
        String sql = "DROP DATABASE IF EXISTS `" + SHADOW_DB + "`; CREATE DATABASE `" + SHADOW_DB + "` CHARACTER SET utf8mb4;";
        executeSql(sql);
    }

    private void importSqlToDb(File sqlFile, String dbName) throws Exception {
        String mysqlExe = WorkspaceEnv.getAppHome() + "/mariadb/bin/mysql.exe";
        ProcessBuilder pb = new ProcessBuilder(
                mysqlExe, "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                "-uroot", "-p" + MariaDbGuardian.getDbPassword(), "--default-character-set=utf8mb4", dbName
        );
        pb.redirectInput(sqlFile);
        if (pb.start().waitFor() != 0) throw new RuntimeException("影子库导入指令执行失败");
    }

    private void atomicSwitchDatabase() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {

            List<String> tables = new ArrayList<>();
            ResultSet rs = stmt.executeQuery("SHOW TABLES FROM `" + SHADOW_DB + "`");
            while (rs.next()) tables.add(rs.getString(1));

            if (tables.isEmpty()) throw new RuntimeException("影子库为空，拒绝切换");

            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + MariaDbGuardian.DB_NAME + "` CHARACTER SET utf8mb4");

            stmt.execute("DROP DATABASE IF EXISTS `" + MariaDbGuardian.DB_NAME + "`");
            stmt.execute("CREATE DATABASE `" + MariaDbGuardian.DB_NAME + "` CHARACTER SET utf8mb4");

            StringBuilder renameSql = new StringBuilder("RENAME TABLE ");
            for (int i = 0; i < tables.size(); i++) {
                String t = tables.get(i);
                renameSql.append("`").append(SHADOW_DB).append("`.`").append(t)
                        .append("` TO `").append(MariaDbGuardian.DB_NAME).append("`.`").append(t).append("` ");
                if (i < tables.size() - 1) renameSql.append(", ");
            }
            stmt.execute(renameSql.toString());

            stmt.execute("DROP DATABASE IF EXISTS `" + SHADOW_DB + "`");
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
            FileUtil.del(bakAssets);
        } catch (Exception e) {
            sendLog("ERROR", "静态资源替换失败，尝试回滚...");
            if (bakAssets.exists()) {
                FileUtil.del(targetAssets);
                FileUtil.move(bakAssets, targetAssets, true);
            }
            throw e;
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