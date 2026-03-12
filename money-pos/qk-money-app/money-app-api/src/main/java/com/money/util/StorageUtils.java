package com.money.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import org.springframework.web.multipart.MultipartFile;

public class StorageUtils {

    /**
     * 统一生成受控的 ObjectKey
     * 1. 强制后缀名正则清洗，只允许字母数字
     * 2. 强制使用 yyyy-MM-dd/uuid.ext 拓扑
     */
    public static String generateObjectKey(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extName = FileUtil.extName(originalName);

        // 核心防御 1：强制清洗后缀，防止恶意后缀攻击 (如 .jsp.xxx)
        if (extName != null) {
            extName = extName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        }
        if (extName == null || extName.isEmpty()) {
            extName = "bin";
        }

        // 核心防御 2：使用 UUID 彻底切断与原始文件名的关联
        return DateUtil.today() + "/" + IdUtil.fastSimpleUUID() + "." + extName;
    }

    /**
     * 稳健的 URL 拼接，处理 Domain 末尾斜杠的各种灵异情况
     */
    public static String concatUrl(String domain, String objectKey) {
        String cleanDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        // 统一使用 Linux 风格的斜杠作为 URL 分隔符
        String cleanKey = objectKey.replace("\\", "/");
        return cleanDomain + "/" + (cleanKey.startsWith("/") ? cleanKey.substring(1) : cleanKey);
    }
}