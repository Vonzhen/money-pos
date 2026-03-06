package com.money.controller;

import com.money.web.vo.PageVO;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.entity.OmsOrderDetail;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 */
@Tag(name = "omsOrder", description = "订单表")
@RestController
@RequestMapping("/oms/order")
@RequiredArgsConstructor
public class OmsOrderController {

    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public PageVO<OmsOrderVO> list(@Validated OmsOrderQueryDTO queryDTO) {
        return omsOrderService.list(queryDTO);
    }

    @Operation(summary = "订单统计")
    @GetMapping("/count")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public OrderCountVO orderCount(LocalDateTime startTime, LocalDateTime endTime) {
        return omsOrderService.countOrderAndSales(startTime, endTime);
    }

    @Operation(summary = "订单详情 (按ID)")
    @GetMapping("/detail")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public OrderDetailVO orderDetail(@RequestParam Long id) {
        return omsOrderService.getOrderDetail(id);
    }

    @Operation(summary = "订单明细列表 (按订单号)")
    @GetMapping("/detailByOrderNo")
    public List<OmsOrderDetail> detailByOrderNo(@RequestParam String orderNo) {
        return omsOrderDetailService.lambdaQuery().eq(OmsOrderDetail::getOrderNo, orderNo).list();
    }

    @Operation(summary = "完整订单详情 (按订单号穿透)")
    @GetMapping("/fullDetailByOrderNo")
    public OrderDetailVO fullDetailByOrderNo(@RequestParam String orderNo) {
        com.money.entity.OmsOrder order = omsOrderService.lambdaQuery().eq(com.money.entity.OmsOrder::getOrderNo, orderNo).one();
        if (order != null) {
            OrderDetailVO detailVO = omsOrderService.getOrderDetail(order.getId());

            // 🌟 贯彻老板的解耦架构：后端绝对掌管财务计算！
            if (detailVO != null && detailVO.getOrder() != null) {
                java.math.BigDecimal total = detailVO.getOrder().getTotalAmount() != null ? detailVO.getOrder().getTotalAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal pay = detailVO.getOrder().getPayAmount() != null ? detailVO.getOrder().getPayAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal coupon = detailVO.getOrder().getCouponAmount() != null ? detailVO.getOrder().getCouponAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal voucher = detailVO.getOrder().getUseVoucherAmount() != null ? detailVO.getOrder().getUseVoucherAmount() : java.math.BigDecimal.ZERO;

                // 核心商业公式：整单优惠 = 总价 - 实付 - 会员单品优惠 - 满减优惠
                java.math.BigDecimal manual = total.subtract(pay).subtract(coupon).subtract(voucher);
                if (manual.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    manual = java.math.BigDecimal.ZERO;
                }

                // 将算好的结果放进包装盒，前端直接拿去显示
                detailVO.getOrder().setManualDiscountAmount(manual);
            }
            return detailVO;
        }
        return null;
    }

    @Operation(summary = "退单")
    @DeleteMapping("/returnOrder")
    @PreAuthorize("@rbac.hasPermission('omsOrder:edit')")
    public void returnOrder(@RequestBody Set<Long> ids) {
        omsOrderService.returnOrder(ids);
    }

    @Operation(summary = "退货")
    @DeleteMapping("/returnGoods")
    @PreAuthorize("@rbac.hasPermission('omsOrder:edit')")
    public void returnGoods(@Valid @RequestBody ReturnGoodsDTO returnGoodsDTO) {
        omsOrderService.returnGoods(returnGoodsDTO);
    }
}