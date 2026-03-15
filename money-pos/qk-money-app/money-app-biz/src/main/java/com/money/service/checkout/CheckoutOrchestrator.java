package com.money.service.checkout;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderLog;
import com.money.service.OmsOrderLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 🌟 结算大总管 (Orchestrator)
 * 核心职责：调度六大服务，记录审计日志，控制事务边界。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutOrchestrator {

    // 注入流水线上的 6 个工人和 1 个日志服务
    private final CheckoutValidationService validationService;
    private final CheckoutPricingService pricingService;
    private final CheckoutOrderService orderService;
    private final CheckoutInventoryService inventoryService;
    private final CheckoutMemberAssetService memberAssetService;
    private final CheckoutPaymentService paymentService;
    private final OmsOrderLogService omsOrderLogService;

    @Transactional(rollbackFor = Exception.class)
    public SettleResultVO orchestrate(SettleAccountsDTO request) {
        log.info("🚀 启动结算流水线，单号: {}", request.getReqId());

        CheckoutContext context = new CheckoutContext();
        context.setRequest(request);

        // 🌟 六大工序，依次执行，任何一步报错，全部回滚！
        validationService.validate(context);
        pricingService.calculate(context);
        orderService.createOrder(context);
        inventoryService.deductStock(context);
        memberAssetService.handleAsset(context);
        paymentService.handlePayment(context);

        // 🌟 补齐：记录高可用的 JSON 结构化审计日志
        saveAuditLog(context);

        // 🌟 组装给顾客的最终小票
        return buildFinalResult(context);
    }

    /**
     * 内部动作：记录审计日志
     */
    private void saveAuditLog(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        NormalizedPaymentResult payResult = context.getPaymentResult();

        int totalItemCount = context.getOrderDetails().stream().mapToInt(d -> d.getQuantity()).sum();
        String distinctPayMethods = payResult.getValidItems().stream()
                .map(p -> StrUtil.isNotBlank(p.getPayTag()) ? p.getMethodCode() + ":" + p.getPayTag() : p.getMethodCode())
                .distinct()
                .collect(Collectors.joining(","));

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());

        Map<String, Object> auditMap = new LinkedHashMap<>(); // 保持插入顺序
        auditMap.put("action", "SETTLE_SUCCESS");
        auditMap.put("orderNo", order.getOrderNo());
        auditMap.put("memberId", context.getRequest().getMember());
        auditMap.put("itemCount", totalItemCount);
        auditMap.put("detailCount", context.getOrderDetails().size());
        auditMap.put("payMethods", distinctPayMethods);
        auditMap.put("finalPay", order.getPayAmount());
        auditMap.put("totalPaid", payResult.getTotalPaid());
        auditMap.put("change", payResult.getChangeAmount());
        auditMap.put("net", payResult.getNetReceived());

        orderLog.setDescription(JSONUtil.toJsonStr(auditMap));
        omsOrderLogService.save(orderLog);
    }

    /**
     * 内部动作：把公文包里的数据，整理成小票返回
     */
    private SettleResultVO buildFinalResult(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        NormalizedPaymentResult payResult = context.getPaymentResult();

        SettleResultVO vo = new SettleResultVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFinalPayAmount(order.getPayAmount());
        vo.setTotalPaid(payResult.getTotalPaid());
        vo.setChangeAmount(payResult.getChangeAmount());
        vo.setNetReceived(payResult.getNetReceived());
        vo.setPaymentTime(order.getPaymentTime());
        vo.setMemberName(order.getMember());
        vo.setCouponDeduct(order.getCouponAmount());
        vo.setVoucherDeduct(order.getUseVoucherAmount());
        vo.setManualDeduct(order.getManualDiscountAmount());

        return vo;
    }
}