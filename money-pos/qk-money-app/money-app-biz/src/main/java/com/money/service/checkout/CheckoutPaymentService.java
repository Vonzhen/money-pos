package com.money.service.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.entity.OmsOrderPay;
import com.money.mapper.OmsOrderPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 结算流水线第六关：出纳员 (增加真实流水回显能力)
 */
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;

    public void loadExistingPayments(CheckoutContext context) {
        String orderNo = context.getOrder().getOrderNo();
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(
                new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo)
        );

        NormalizedPaymentResult result = new NormalizedPaymentResult();
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal changeTotal = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            NormalizedPaymentResult.StandardPayItem item = new NormalizedPaymentResult.StandardPayItem();
            item.setMethodCode(pay.getPayMethodCode());
            item.setMethodName(pay.getPayMethodName());
            item.setPayTag(pay.getPayTag());

            BigDecimal net = pay.getNetAmount() != null ? pay.getNetAmount() : pay.getPayAmount();
            BigDecimal orig = pay.getOriginalAmount() != null ? pay.getOriginalAmount() : net;
            BigDecimal change = pay.getChangeAllocated() != null ? pay.getChangeAllocated() : BigDecimal.ZERO;

            item.setNetAmount(net);
            item.setOriginalAmount(orig);
            item.setChangeAmount(change);

            result.getValidItems().add(item);
            netTotal = netTotal.add(net);
            originalTotal = originalTotal.add(orig);
            changeTotal = changeTotal.add(change);
        }

        result.setNetReceived(netTotal);
        result.setTotalPaid(originalTotal);
        result.setChangeAmount(changeTotal);
        context.setPaymentResult(result);
    }

    public void handlePayment(CheckoutContext context) {
        NormalizedPaymentResult payResult = context.getPaymentResult();
        String orderNo = context.getOrder().getOrderNo();
        LocalDateTime now = LocalDateTime.now();

        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
            if ((item.getNetAmount() == null || item.getNetAmount().compareTo(BigDecimal.ZERO) == 0) &&
                    (item.getOriginalAmount() == null || item.getOriginalAmount().compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getMethodCode());

            // ==========================================
            // 🌟 修复问题5：【payMethodName 过度信任前端】
            // 绝不直接使用 item.getMethodName()，后端强行匹配！
            // ==========================================
            payRecord.setPayMethodName(getSafeMethodName(item.getMethodCode(), item.getMethodName()));

            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setNetAmount(item.getNetAmount());
            payRecord.setOriginalAmount(item.getOriginalAmount() != null ? item.getOriginalAmount() : item.getNetAmount());
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }

    // 🌟 内部安全映射引擎：即便前端传错或被黑客篡改，落库的依然是标准名称
    private String getSafeMethodName(String code, String fallbackName) {
        if (code == null) return "未知支付";
        switch (code.toUpperCase()) {
            case "WECHAT": return "微信支付";
            case "ALIPAY": return "支付宝";
            case "CASH": return "现金";
            case "BALANCE": return "余额支付";
            case "UNIONPAY": return "银联刷卡";
            // 如果遇到真的没见过的渠道，为了不阻断流程，再降级使用前端传来的名字（可加上[外部]标记防伪）
            default: return fallbackName != null ? fallbackName : "其他渠道";
        }
    }
}