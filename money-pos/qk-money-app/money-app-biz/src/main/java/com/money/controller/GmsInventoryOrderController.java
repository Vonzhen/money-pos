package com.money.controller;

import com.money.dto.inventory.GmsInventoryOrderDTO;
import com.money.service.GmsInventoryOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gms/inventory")
@RequiredArgsConstructor
@Tag(name = "库存单据管理 API")
public class GmsInventoryOrderController {

    private final GmsInventoryOrderService inventoryOrderService;

    @PostMapping("/inbound")
    @Operation(summary = "创建采购入库单")
    public void createInbound(@RequestBody GmsInventoryOrderDTO dto) {
        // 直接执行核心入库和加库存逻辑，不需要再手动返回 Result
        inventoryOrderService.createInboundOrder(dto);
    }
    @PostMapping("/check")
    @Operation(summary = "创建库存盘点单")
    public void createCheck(@RequestBody GmsInventoryOrderDTO dto) {
        inventoryOrderService.createCheckOrder(dto);
    }

    @PostMapping("/outbound")
    @Operation(summary = "创建报损出库单")
    public void createOutbound(@RequestBody GmsInventoryOrderDTO dto) {
        inventoryOrderService.createOutboundOrder(dto);
    }
}