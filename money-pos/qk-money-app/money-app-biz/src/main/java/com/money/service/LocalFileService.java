package com.money.service;

import cn.hutool.core.io.FileUtil;
import com.money.exception.StorageException;
import com.money.util.StorageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * 单机版专属本地文件存储服务 (严格遵循规范)
 */
@Service
public class LocalFileService {

    // 动态读取 AppWorkspace 注入的 D 盘物理路径
    @Value("${local.bucket}")
    private String localPath;

    // 🌟 核心修复：去掉 http://localhost:9101，只保留相对路径
    // 这样前端拿到 URL 后，会自动用当前的 1520 端口去访问，完美触发代理隧道！
    @Value("${money.storage.local.domain:/money-pos/assets/}")
    private String domain;

    public String upload(MultipartFile file) {
        try {
            // 1. 调用系统原生的 StorageUtils 生成安全的 UUID 文件名
            String objectKey = StorageUtils.generateObjectKey(file);

            // 2. 拼装 D 盘绝对路径
            File dest = new File(localPath, objectKey);

            // 3. 依靠 Hutool 创建多级目录
            FileUtil.mkdir(dest.getParentFile());

            // 4. 物理写入
            file.transferTo(dest);

            // 5. 拼接并返回相对路径 (例如: /money-pos/assets/2026-03-12/xxxx.png)
            return StorageUtils.concatUrl(domain, objectKey);

        } catch (IOException e) {
            throw new StorageException("本地文件物理写入失败", e);
        }
    }
}