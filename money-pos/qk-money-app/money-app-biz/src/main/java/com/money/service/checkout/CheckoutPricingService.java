package com.money.service.checkout;

import com.money.constant.PayMethodEnum;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.PricingResult; // 🌟 引入新契约
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.service.impl.PosCalculationEngine;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🌟 结算流水线第二关：精算师
 */
@Service
@RequiredArgsConstructor
public class CheckoutPricingService {

    private final PosCalculationEngine posCalculationEngine;

    public void calculate(CheckoutContext context) {
        SettleAccountsDTO dto = context.getRequest();

        // 1. 委托计价引擎算账
        SettleTrialReqDTO trialReq = new SettleTrialReqDTO();
        trialReq.setMember(dto.getMember());
        trialReq.setUsedCouponRuleId(dto.getUsedCouponRuleId());
        trialReq.setUsedCouponCount(dto.getUsedCouponCount());
        trialReq.setWaiveCoupon(dto.getWaiveCoupon());
        trialReq.setManualDiscountAmount(dto.getManualDiscountAmount());

        trialReq.setItems(dto.getOrderDetail().stream().map(d -> {
            SettleTrialReqDTO.TrialItem item = new SettleTrialReqDTO.TrialItem();
            item.setGoodsId(d.getGoodsId());
            item.setQuantity(d.getQuantity());
            return item;
        }).collect(Collectors.toList()));

        // 🌟 核心替换：拿到权威的“真理裁决书”
        PricingResult trialRes = posCalculationEngine.calculate(trialReq);
        BigDecimal finalPayAmount = trialRes.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP);

        // 👉 将计价结果装入公文包
        context.setPricingResult(trialRes);

        // 2. 支付金额清洗 (基于真理引擎输出的最终应付)
        NormalizedPaymentResult payResult = normalizePayments(dto.getPayments(), finalPayAmount);
        context.setPaymentResult(payResult);
    }

    private NormalizedPaymentResult normalizePayments(List<SettleAccountsDTO.PaymentItem> rawPayments, BigDecimal finalPayAmount) {
        NormalizedPaymentResult result = new NormalizedPaymentResult();

        for (SettleAccountsDTO.PaymentItem p : rawPayments) {
            if (p.getPayAmount() == null || p.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal itemPay = p.getPayAmount().setScale(2, RoundingMode.HALF_UP);
            PayMethodEnum methodEnum = PayMethodEnum.fromCode(p.getPayMethodCode());
            boolean isCash = methodEnum.isAllowChange();

            NormalizedPaymentResult.StandardPayItem sItem = new NormalizedPaymentResult.StandardPayItem();
            sItem.setMethodCode(p.getPayMethodCode());
            sItem.setMethodName(p.getPayMethodName());
            sItem.setPayTag(p.getPayTag());
            sItem.setCash(isCash);
            sItem.setOriginalAmount(itemPay);
            sItem.setNetAmount(itemPay);
            result.getValidItems().add(sItem);

            result.setTotalPaid(result.getTotalPaid().add(itemPay));
            if (isCash) {
                result.setCashPaid(result.getCashPaid().add(itemPay));
            } else {
                result.setNonCashPaid(result.getNonCashPaid().add(itemPay));
            }
        }

        if (result.getTotalPaid().compareTo(finalPayAmount) < 0) {
            throw new BaseException(String.format("实付金额不足。本单应收: %s，实收: %s", finalPayAmount, result.getTotalPaid()));
        }
        if (result.getNonCashPaid().compareTo(finalPayAmount) > 0) {
            throw new BaseException("【风控拦截】非现金支付总额超过了应付总额，严禁套现！");
        }

        result.setChangeAmount(result.getTotalPaid().subtract(finalPayAmount));
        result.setNetReceived(result.getTotalPaid().subtract(result.getChangeAmount()));

        BigDecimal remainChange = result.getChangeAmount();
        for (NormalizedPaymentResult.StandardPayItem item : result.getValidItems()) {
            if (item.isCash() && remainChange.compareTo(BigDecimal.ZERO) > 0) {
                if (item.getNetAmount().compareTo(remainChange) >= 0) {
                    item.setNetAmount(item.getNetAmount().subtract(remainChange));
                    remainChange = BigDecimal.ZERO;
                } else {
                    remainChange = remainChange.subtract(item.getNetAmount());
                    item.setNetAmount(BigDecimal.ZERO);
                }
            }
            item.setNetAmount(item.getNetAmount().setScale(2, RoundingMode.HALF_UP));
            item.setOriginalAmount(item.getOriginalAmount().setScale(2, RoundingMode.HALF_UP));
        }

        result.setTotalPaid(result.getTotalPaid().setScale(2, RoundingMode.HALF_UP));
        result.setCashPaid(result.getCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setNonCashPaid(result.getNonCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setChangeAmount(result.getChangeAmount().setScale(2, RoundingMode.HALF_UP));
        result.setNetReceived(result.getNetReceived().setScale(2, RoundingMode.HALF_UP));

        return result;
    }
}