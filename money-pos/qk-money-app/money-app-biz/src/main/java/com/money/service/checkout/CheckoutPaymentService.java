package com.money.service.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.entity.OmsOrderPay;
import com.money.entity.SysDictDetail;
import com.money.mapper.OmsOrderPayMapper;
import com.money.service.SysDictDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 结算流水线第六关：出纳员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;
    private final SysDictDetailService sysDictDetailService;

    public void loadExistingPayments(CheckoutContext context) {
        // ... (保持原代码不变) ...
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
            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayMethodName(getSafeMethodName(item.getMethodCode(), item.getPayTag()));

            // 🌟 核心升级：完整的三元组时空胶囊落库
            payRecord.setPayAmount(item.getNetAmount()); // 兼容老代码
            payRecord.setOriginalAmount(item.getOriginalAmount()); // 原始实收
            payRecord.setNetAmount(item.getNetAmount());           // 财务净额
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }

    private String getSafeMethodName(String methodCode, String payTag) {
        // ... (保持原双层字典逻辑不变) ...
        try {
            if (StringUtils.hasText(payTag)) {
                List<SysDictDetail> subList = sysDictDetailService.listByDict("paySubTag");
                if (subList != null) {
                    for (SysDictDetail detail : subList) {
                        if (payTag.equalsIgnoreCase(detail.getValue())) return detail.getCnDesc();
                    }
                }
            }
            if (StringUtils.hasText(methodCode)) {
                List<SysDictDetail> mainList = sysDictDetailService.listByDict("pos_payment_method");
                if (mainList != null) {
                    for (SysDictDetail detail : mainList) {
                        if (methodCode.equalsIgnoreCase(detail.getValue())) return detail.getCnDesc();
                    }
                }
            }
        } catch (Exception e) {
            log.error("💥 动态匹配支付方式字典失败，降级到安全兜底模式。Code:{}, Tag:{}", methodCode, payTag, e);
        }
        String code = StringUtils.hasText(payTag) ? payTag : methodCode;
        return "其他渠道(" + code + ")";
    }
}