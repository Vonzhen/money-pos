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
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 工业级一键灾备与还原引擎 (V4.2 完整集成版：精准端口 + SSE大屏推送)
 */
@Slf4j
@Service
public class SysBackupService {

    private static final String MANIFEST_FILE = "backup-manifest.json";
    private static final String APP_VERSION = "2.2.0";

    // ================== SSE 实时日志广播机制 ==================
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时
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
    // ==========================================================

    /**
     * 🌟 一键热备：支持自动区分是“日常备份”还是“还原前快照”
     */
    public File createBackupZip(String prefix) {
        String baseDir = WorkspaceEnv.getAppHome();
        String backupDir = baseDir + File.separator + "backups";
        String mariadbBin = baseDir + File.separator + "mariadb" + File.separator + "bin";
        String tempBatchDir = backupDir + File.separator + "temp_" + IdUtil.fastSimpleUUID();

        FileUtil.mkdir(tempBatchDir);

        try {
            log.info("📦 [灾备引擎] 正在执行全量热备...");

            // 1. 生成 Manifest
            JSONObject manifest = new JSONObject();
            manifest.set("appName", "MoneyPOS");
            manifest.set("appVersion", APP_VERSION);
            manifest.set("backupTime", DateUtil.now());
            manifest.set("dbName", MariaDbGuardian.DB_NAME);
            FileUtil.writeUtf8String(manifest.toStringPretty(), tempBatchDir + File.separator + MANIFEST_FILE);

            // 2. 工业级 mysqldump 导出 (加入 --host 和 --port 解决连错库报错)
            File sqlFile = new File(tempBatchDir + File.separator + "money_pos.sql");
            File mysqldumpExe = new File(mariadbBin + File.separator + "mysqldump.exe");

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpExe.getAbsolutePath(),
                    "--host=127.0.0.1",
                    "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot",
                    "-p" + MariaDbGuardian.getDbPassword(),
                    "--single-transaction",
                    "--routines", "--triggers",
                    "--hex-blob",
                    "--add-drop-table",
                    "--default-character-set=utf8mb4",
                    MariaDbGuardian.DB_NAME
            );

            pb.redirectOutput(sqlFile);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    if (line.toLowerCase().contains("error")) log.error("[mysqldump] {}", line);
                }
            }

            if (process.waitFor() != 0) throw new RuntimeException("数据库导出指令执行失败");

            // 3. 拷贝静态资源
            File assetsDir = new File(baseDir + File.separator + "assets");
            if (assetsDir.exists()) FileUtil.copy(assetsDir, new File(tempBatchDir), true);

            // 4. 打包并返回
            String zipFileName = prefix + "MoneyPOS_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss") + ".zip";
            File zipFile = new File(backupDir + File.separator + zipFileName);
            cn.hutool.core.util.ZipUtil.zip(tempBatchDir, zipFile.getAbsolutePath(), true);

            log.info("✅ [灾备引擎] 备份就绪: {}", zipFile.getName());
            return zipFile;

        } catch (Exception e) {
            log.error("❌ [灾备引擎] 备份致命错误", e);
            throw new RuntimeException("底层备份服务异常，请查看日志");
        } finally {
            FileUtil.del(tempBatchDir);
        }
    }

    /**
     * 🌟 灾难还原：带实时大屏推送与核武级覆盖
     */
    public void restoreFromZip(MultipartFile backupFile) {
        String baseDir = WorkspaceEnv.getAppHome();
        String tempRestoreDir = baseDir + File.separator + "backups" + File.separator + "restore_" + IdUtil.fastSimpleUUID();
        String mariadbBin = baseDir + File.separator + "mariadb" + File.separator + "bin";

        try {
            log.warn("⚠️ [灾备引擎] 启动系统灾难还原流程...");
            sendLog("WARN", "=== 系统灾难还原序列启动 ===");

            // 1. 安全解压
            sendLog("INFO", "接收到归档文件，开始安全解压与防穿越校验...");
            File zipFile = new File(tempRestoreDir + ".zip");
            backupFile.transferTo(zipFile);
            FileUtil.mkdir(tempRestoreDir);

            try (ZipFile zip = new ZipFile(zipFile)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    File destFile = new File(tempRestoreDir, entry.getName());
                    if (!destFile.getCanonicalPath().startsWith(new File(tempRestoreDir).getCanonicalPath())) {
                        sendLog("ERROR", "检测到非法的压缩包路径注入风险，终止！");
                        throw new RuntimeException("检测到非法的压缩包路径注入风险！");
                    }
                }
            }
            cn.hutool.core.util.ZipUtil.unzip(zipFile, new File(tempRestoreDir));
            sendLog("INFO", "解压完成，正在递归寻址核心数据...");

            // 2. 校验
            List<File> sqlFiles = FileUtil.loopFiles(new File(tempRestoreDir), pathname -> pathname.getName().endsWith(".sql"));
            if (sqlFiles.isEmpty()) throw new RuntimeException("备份包损坏：未找到核心 SQL 数据文件！");
            File sqlFile = sqlFiles.get(0);

            List<File> manifestFiles = FileUtil.loopFiles(new File(tempRestoreDir), pathname -> pathname.getName().equals(MANIFEST_FILE));
            if (!manifestFiles.isEmpty()) {
                JSONObject manifest = JSONUtil.readJSONObject(manifestFiles.get(0), StandardCharsets.UTF_8);
                log.info("📄 [灾备引擎] 识别到备份包来源: {}, 版本: {}", manifest.getStr("appName"), manifest.getStr("appVersion"));
                sendLog("INFO", "读取到防灾元数据：适用版本 " + manifest.getStr("appVersion"));
            }

            // 3. 快照
            sendLog("INFO", "正在为您自动创建前置保护快照，以防还原失败...");
            createBackupZip("PreRestore_");
            sendLog("SUCCESS", "前置保护快照创建成功！保存在 backups 目录。");

            // 4. 重建库
            sendLog("WARN", "第一阶段：开始彻底抹除旧数据并重建数据库内核...");
            File mysqlExe = new File(mariadbBin + File.separator + "mysql.exe");
            ProcessBuilder resetPb = new ProcessBuilder(
                    mysqlExe.getAbsolutePath(),
                    "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot", "-p" + MariaDbGuardian.getDbPassword(),
                    "-e", "DROP DATABASE IF EXISTS `" + MariaDbGuardian.DB_NAME + "`; CREATE DATABASE `" + MariaDbGuardian.DB_NAME + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
            );
            resetPb.redirectErrorStream(true);
            if (resetPb.start().waitFor() != 0) throw new RuntimeException("重置物理数据库失败！");
            sendLog("SUCCESS", "旧数据抹除完毕，内核重建成功！");

            // 5. 导入数据
            sendLog("INFO", "正在将 SQL 数据流物理灌入数据库，请勿断电...");
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlExe.getAbsolutePath(),
                    "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot", "-p" + MariaDbGuardian.getDbPassword(),
                    "--default-character-set=utf8mb4", MariaDbGuardian.DB_NAME
            );
            pb.redirectInput(sqlFile);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    if (line.toLowerCase().contains("error")) {
                        log.error("[mysql import] {}", line);
                        sendLog("ERROR", "[底层日志] " + line);
                    }
                }
            }
            if (process.waitFor() != 0) throw new RuntimeException("SQL 数据灌入失败！请使用前置快照恢复。");
            sendLog("SUCCESS", "数据表与业务记录覆写完毕！");

            // 6. 还原资源
            sendLog("INFO", "第二阶段：正在还原商品图片等静态资源文件...");
            List<File> assetsDirs = FileUtil.loopFiles(new File(tempRestoreDir), pathname -> pathname.isDirectory() && pathname.getName().equals("assets"));
            if (!assetsDirs.isEmpty()) {
                File targetAssets = new File(baseDir + File.separator + "assets");
                FileUtil.del(targetAssets);
                FileUtil.copyContent(assetsDirs.get(0), targetAssets, true);
            }
            sendLog("SUCCESS", "静态资源覆盖完成！");
            sendLog("SUCCESS", "=== 灾难还原全流程完美收官 ===");

        } catch (Exception e) {
            sendLog("ERROR", "❌ 还原遭遇异常中断: " + e.getMessage());
            log.error("❌ [灾备引擎] 还原过程遭遇毁灭性拦截", e);
            throw new RuntimeException(StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : "底层还原服务异常，系统可能处于半恢复状态！");
        } finally {
            FileUtil.del(tempRestoreDir);
            FileUtil.del(tempRestoreDir + ".zip");
        }
    }
}