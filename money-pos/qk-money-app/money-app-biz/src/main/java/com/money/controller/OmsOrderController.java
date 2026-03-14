package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.money.constant.BizErrorStatus;
import com.money.dto.OmsOrder.*;
import com.money.service.OmsOrderService;
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

@Tag(name = "oms-order", description = "订单与营销管理 (大一统重构版)")
@RestController
@RequestMapping("/oms-order")
@RequiredArgsConstructor
public class OmsOrderController {

    private final OmsOrderService omsOrderService;

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
        return omsOrderService.countOrderAndSales(startTime, endTime);
    }

    // ==========================================
    // 🌟 修复 404: 详情接口“双擎升级”
    // ==========================================
    @Operation(summary = "订单详情 (支持ID或单号)")
    @GetMapping("/detail")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderDetailVO getOrderDetail(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String orderNo) {

        if (id == null && StrUtil.isBlank(orderNo)) {
            // 🌟 修复：直接传枚举对象，去掉 .getCode()
            throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "必须提供订单ID或订单编号");
        }

        // 优先使用业务单号查询，其次使用数据库主键
        if (StrUtil.isNotBlank(orderNo)) {
            return omsOrderService.getOrderDetailByNo(orderNo);
        }
        return omsOrderService.getOrderDetail(id);
    }

    // ==========================================
    // 🌟 修复 JSON 炸弹与幂等缺失: 独立请求体 DTO
    // ==========================================
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
        omsOrderService.returnOrder(reqDTO.getOrderNo());
    }

    @Operation(summary = "部分商品退货 (资金逆向操作)")
    @PostMapping("/returnGoods")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnGoods(@Validated @RequestBody ReturnGoodsDTO returnGoodsDTO) {
        omsOrderService.returnGoods(returnGoodsDTO);
    }

    @Operation(summary = "利润审计分页")
    @GetMapping("/profit-audit")
    @PreAuthorize("@rbac.hasPermission('oms:order:audit')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsOrderService.getProfitAuditPage(queryDTO);
    }
}