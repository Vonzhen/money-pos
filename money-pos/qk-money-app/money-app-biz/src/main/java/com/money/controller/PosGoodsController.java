package com.money.controller;

import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.service.GoodsPosFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专门服务于收银台的独立入口
 * (实现了业务解耦，且保证前端接口路径 100% 无损兼容)
 */
@Tag(name = "posGoods", description = "POS收银台商品专线接口")
@RestController
@RequiredArgsConstructor
public class PosGoodsController {

    // 🌟 直接调用刚才新建的中台服务
    private final GoodsPosFacade goodsPosFacade;

    // 🌟 URL 完美保持不变！前端完全无感！
    @GetMapping("/gms/goods/pos-search")
    @Operation(summary = "POS全能搜索(条码/名称/助记码) - 架构级策略过滤")
    public List<GmsGoodsVO> posSearchGoods(@RequestParam String keyword) {
        return goodsPosFacade.posSearchGoods(keyword);
    }
}