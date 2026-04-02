package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.GmsGoods.GmsStockLogQueryDTO;
import com.money.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "gmsStockLog", description = "进销存台账")
@RestController
@RequestMapping("/gms/stockLog")
@RequiredArgsConstructor
public class GmsStockLogController {

    private final GmsStockLogMapper gmsStockLogMapper;

    @Operation(summary = "分页查询库存流水")
    @GetMapping
    public PageVO<GmsStockLog> list(GmsStockLogQueryDTO queryDTO, @RequestParam(required = false) String goodsBarcode) {
        Page<GmsStockLog> page = gmsStockLogMapper.selectPage(
                PageUtil.toPage(queryDTO),
                new LambdaQueryWrapper<GmsStockLog>()
                        // 🌟 核心修复：接收前端传来的条码，进行绝对精准的匹配！
                        .eq(StrUtil.isNotBlank(goodsBarcode), GmsStockLog::getGoodsBarcode, goodsBarcode)
                        // 兜底保留：如果单纯输入文字，依然支持按名称模糊查询
                        .like(StrUtil.isNotBlank(queryDTO.getGoodsName()), GmsStockLog::getGoodsName, queryDTO.getGoodsName())
                        .eq(StrUtil.isNotBlank(queryDTO.getType()), GmsStockLog::getType, queryDTO.getType())
                        .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), GmsStockLog::getOrderNo, queryDTO.getOrderNo())
                        .orderByDesc(GmsStockLog::getCreateTime)
        );
        return PageUtil.toPageVO(page, GmsStockLog::new);
    }
}