package com.money.controller;

import com.money.entity.SysDictDetail;
import com.money.service.LocalFileService;
import com.money.service.SysDictDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用业务处理收发室
 * 兼任：客显大屏 CMS 配置下发中心
 */
@Tag(name = "通用接口")
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class CommonController {

    private final LocalFileService localFileService;
    private final SysDictDetailService sysDictDetailService;

    private static final String SETTING_DICT = "SYSTEM_SETTING";
    private static final String DISPLAY_KEY = "customer_display";

    @Operation(summary = "单机版通用文件上传 (自动落盘 D 盘)")
    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        String fileUrl = localFileService.upload(file);
        Map<String, String> data = new HashMap<>();
        data.put("url", fileUrl);
        return data;
    }

    @Operation(summary = "获取客显配置 (包含营销阈值与多码资产)")
    @GetMapping("/display-settings")
    public String getDisplaySettings() {
        SysDictDetail detail = sysDictDetailService.lambdaQuery()
                .eq(SysDictDetail::getDict, SETTING_DICT)
                .eq(SysDictDetail::getValue, DISPLAY_KEY)
                .one();
        return detail != null ? detail.getCnDesc() : "{}";
    }

    @Operation(summary = "保存客显配置 (无模式 JSON 文档仓)")
    @PutMapping("/display-settings")
    public void saveDisplaySettings(@RequestBody Map<String, Object> settingsMap) {
        /*
         * 数据契约 (Data Contract):
         * - welcomeText: 滚动标语
         * - promoThreshold: 营销满减触发差额 (如 20元)
         * - promoTemplate: 营销诱导文案模板
         * - paymentCodes: 动态收款码资产数组 [{name, url, isDefault}]
         */
        SysDictDetail target = sysDictDetailService.lambdaQuery()
                .eq(SysDictDetail::getDict, SETTING_DICT)
                .eq(SysDictDetail::getValue, DISPLAY_KEY)
                .one();

        if (target == null) {
            throw new RuntimeException("配置未初始化，请检查SQL是否执行");
        }

        // 🌟 架构优势：无限兼容前端新增的配置项，直接序列化为 TEXT 存入 MariaDB
        target.setCnDesc(JSON.toJSONString(settingsMap));
        sysDictDetailService.updateById(target);
    }
}