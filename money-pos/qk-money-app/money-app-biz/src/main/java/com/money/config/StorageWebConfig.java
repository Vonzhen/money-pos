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
        // 确保使用标准的 file: 协议前缀，并且在 Windows/Linux 下都适用
        File localDir = new File(localPath);
        String location = localDir.toURI().toString();

        if (!location.endsWith("/")) {
            location += "/";
        }

        // 宽容映射：无论前端请求是 /assets/** 还是 /money-pos/assets/** 都能被正确拦截并转发到 D 盘
        registry.addResourceHandler("/assets/**", "/money-pos/assets/**")
                .addResourceLocations(location);

        System.out.println("=================================================");
        System.out.println("[MoneyPOS 图像引擎] 静态资源隧道已开启映射:");
        System.out.println(" /assets/** -> " + location);
        System.out.println(" /money-pos/assets/** -> " + location);
        System.out.println("=================================================");
    }
}