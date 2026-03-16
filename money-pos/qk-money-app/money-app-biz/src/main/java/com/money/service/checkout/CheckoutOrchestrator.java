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
 * 核心升级：增加幂等拦截闸口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutOrchestrator {

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

        // ================= 🌟 1. 幂等拦截闸口 =================
        if (orderService.loadExistingOrder(context)) {
            log.info("【幂等拦截生效】检测到重复请求，直接返回已成功结果，跳过所有资产扣减！单号: {}", request.getReqId());
            // 找回支付流水拼装小票
            paymentService.loadExistingPayments(context);
            // 拦截放行，直接返回小票
            return buildFinalResult(context);
        }

        // ================= 🌟 2. 正常流水线 =================
        validationService.validate(context);
        pricingService.calculate(context);
        orderService.createOrder(context);
        inventoryService.deductStock(context);
        memberAssetService.handleAsset(context);
        paymentService.handlePayment(context);

        saveAuditLog(context);

        return buildFinalResult(context);
    }

    private void saveAuditLog(CheckoutContext context) {
        // ... 原有逻辑不变 ...
        OmsOrder order = context.getOrder();
        NormalizedPaymentResult payResult = context.getPaymentResult();

        int totalItemCount = context.getOrderDetails().stream().mapToInt(d -> d.getQuantity()).sum();
        String distinctPayMethods = payResult.getValidItems().stream()
                .map(p -> StrUtil.isNotBlank(p.getPayTag()) ? p.getMethodCode() + ":" + p.getPayTag() : p.getMethodCode())
                .distinct()
                .collect(Collectors.joining(","));

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());

        Map<String, Object> auditMap = new LinkedHashMap<>();
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