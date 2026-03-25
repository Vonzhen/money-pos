package com.money.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 工业级静态资源映射隧道 (V2.4 宽容版)
 */
@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    @Value("${local.bucket}")
    private String localPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File localDir = new File(localPath);

        // 🌟 核心修复：防止“首次启动必须重启”Bug
        // 如果目录不存在，强行在启动时就把它建出来，逼迫 Spring Boot 乖乖建立映射隧道！
        if (!localDir.exists()) {
            localDir.mkdirs();
        }

        String location = localDir.toURI().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }

        registry.addResourceHandler("/assets/**", "/money-pos/assets/**")
                .addResourceLocations(location);

        // ... 下面打印日志的代码保持不变

        System.out.println("=================================================");
        System.out.println("[MoneyPOS 图像引擎] 静态资源隧道已开启映射:");
        System.out.println(" /assets/** -> " + location);
        System.out.println(" /money-pos/assets/** -> " + location);
        System.out.println("=================================================");
    }
}