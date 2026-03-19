package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.money.constant.BizErrorStatus;
import com.money.dto.OmsOrder.*;
// 🌟 引入 V8.0 轻拆分后的“三驾马车”与硬件驱动
import com.money.service.OmsOrderService;
import com.money.service.OmsOrderRefundService;
import com.money.service.OmsSalesAnalysisService;
import com.money.service.printer.PosPrinterService;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Tag(name = "oms-order", description = "订单与营销管理 (V8.0 轻拆分稳定版)")
@RestController
@RequestMapping("/oms-order")
@RequiredArgsConstructor
public class OmsOrderController {

    private final OmsOrderService omsOrderService; // 大总管：负责查
    private final OmsOrderRefundService omsOrderRefundService; // 法医：负责退款
    private final OmsSalesAnalysisService omsSalesAnalysisService; // 会计：负责报表
    private final PosPrinterService posPrinterService; // 🚀 硬件车间主任：负责开钱箱和打印

    @Operation(summary = "订单分页列表")
    @GetMapping("/page")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        return omsOrderService.list(queryDTO);
    }

    @Operation(summary = "订单统计看板")
    @GetMapping("/statistics")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderCountVO statistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        // 统计报表归会计管
        return omsSalesAnalysisService.countOrderAndSales(startTime, endTime);
    }

    @Operation(summary = "订单详情 (支持ID或单号)")
    @GetMapping("/detail")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderDetailVO getOrderDetail(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String orderNo) {

        if (id == null && StrUtil.isBlank(orderNo)) {
            throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "必须提供订单ID或订单编号");
        }
        if (StrUtil.isNotBlank(orderNo)) {
            return omsOrderService.getOrderDetailByNo(orderNo);
        }
        return omsOrderService.getOrderDetail(id);
    }

    @Data
    public static class ReturnOrderReqDTO {
        @NotBlank(message = "退款单号不能为空")
        private String orderNo;
        @NotBlank(message = "缺少防重放标识 reqId")
        private String reqId;
    }

    @Operation(summary = "整单退款 (接入幂等保护)")
    @PostMapping("/return")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnOrder(@Validated @RequestBody ReturnOrderReqDTO reqDTO) {
        // 退款归特种部队管
        omsOrderRefundService.returnOrder(reqDTO.getOrderNo());
    }

    @Operation(summary = "部分商品退货 (资金逆向操作)")
    @PostMapping("/returnGoods")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnGoods(@Validated @RequestBody ReturnGoodsDTO returnGoodsDTO) {
        // 退款归特种部队管
        omsOrderRefundService.returnGoods(returnGoodsDTO);
    }

    @Operation(summary = "利润审计分页")
    @GetMapping("/profit-audit")
    @PreAuthorize("@rbac.hasPermission('oms:order:audit')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        // 审计报表归会计管
        return omsSalesAnalysisService.getProfitAuditPage(queryDTO);
    }

    @Operation(summary = "硬件级：打印小票并弹开钱箱")
    @GetMapping("/hardware/print")
    public Boolean printHardwareReceipt(@RequestParam String orderNo) {
        // 1. 查出完整的订单快照
        OrderDetailVO orderVO = omsOrderService.getOrderDetailByNo(orderNo);

        // 2. 调用硬件打印机 (店名、电话等配置，Service 内部会自动去查数据库，不需要传参数了)
        posPrinterService.printReceiptAndOpenDrawer(orderVO);

        // 3. 直接返回 true，避免报找不到 Result 类的错
        return true;
    }
}