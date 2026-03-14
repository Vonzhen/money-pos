package com.money.controller;

import com.money.dto.OmsOrder.*;
import com.money.service.OmsOrderService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "oms-order", description = "订单管理")
@RestController
@RequestMapping("/oms-order")
@RequiredArgsConstructor
public class OmsOrderController {

    private final OmsOrderService omsOrderService;

    @Operation(summary = "订单分页列表")
    @GetMapping("/page")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        // 🌟 对应重构后的 Service.list(queryDTO)
        return omsOrderService.list(queryDTO);
    }

    @Operation(summary = "订单统计")
    @GetMapping("/statistics")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderCountVO statistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        // 🌟 对应重构后的统计接口
        return omsOrderService.countOrderAndSales(startTime, endTime);
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderDetailVO getOrderDetail(@PathVariable Long id) {
        // 🌟 对应重构后的详情接口
        return omsOrderService.getOrderDetail(id);
    }

    @Operation(summary = "整单退款")
    @PostMapping("/return")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnOrder(@RequestBody String orderNo) {
        // 🌟 核心：改为接收单号，执行幂等退款
        // 注意：如果前端传的是JSON {"orderNo": "xxx"}, 需要对应处理，这里先保证编译
        omsOrderService.returnOrder(orderNo);
    }

    @Operation(summary = "部分商品退货")
    @PostMapping("/returnGoods")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnGoods(@Validated @RequestBody ReturnGoodsDTO returnGoodsDTO) {
        // 🌟 对应重构后的部分退货接口
        omsOrderService.returnGoods(returnGoodsDTO);
    }

    @Operation(summary = "利润审计分页")
    @GetMapping("/profit-audit")
    @PreAuthorize("@rbac.hasPermission('oms:order:audit')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsOrderService.getProfitAuditPage(queryDTO);
    }
}