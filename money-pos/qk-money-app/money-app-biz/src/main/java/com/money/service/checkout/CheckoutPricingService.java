package com.money.service.checkout;

import com.money.constant.PayMethodEnum;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.PricingResult;
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
 * 🌟 结算流水线第二关：精算师 (权威真理源)
 */
@Service
@RequiredArgsConstructor
public class CheckoutPricingService {

    private final PosCalculationEngine posCalculationEngine;

    public void calculate(CheckoutContext context) {
        SettleAccountsDTO dto = context.getRequest();

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

        PricingResult trialRes = posCalculationEngine.calculate(trialReq);
        BigDecimal finalPayAmount = trialRes.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP);

        context.setPricingResult(trialRes);

        // 🌟 权威分配：将前端的“原始实收”和算出来的“最终应付”交给归一化引擎
        NormalizedPaymentResult payResult = normalizePayments(dto.getPayments(), finalPayAmount);
        context.setPaymentResult(payResult);
    }

    private NormalizedPaymentResult normalizePayments(List<SettleAccountsDTO.PaymentItem> rawPayments, BigDecimal finalPayAmount) {
        NormalizedPaymentResult result = new NormalizedPaymentResult();

        for (SettleAccountsDTO.PaymentItem p : rawPayments) {
            if (p.getPayAmount() == null || p.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            // 🌟 1. 明确语义：前端传来的 payAmount 就是原始实收！
            BigDecimal itemOriginal = p.getPayAmount().setScale(2, RoundingMode.HALF_UP);
            PayMethodEnum methodEnum = PayMethodEnum.fromCode(p.getPayMethodCode());
            boolean isCash = (methodEnum != null && methodEnum.isAllowChange());

            NormalizedPaymentResult.StandardPayItem sItem = new NormalizedPaymentResult.StandardPayItem();
            sItem.setMethodCode(p.getPayMethodCode());
            sItem.setMethodName(p.getPayMethodName());
            sItem.setPayTag(p.getPayTag());
            sItem.setCash(isCash);

            sItem.setOriginalAmount(itemOriginal);
            // 净额初始等于实收，后续扣减找零
            sItem.setNetAmount(itemOriginal);
            sItem.setChangeAmount(BigDecimal.ZERO);

            result.getValidItems().add(sItem);

            result.setTotalPaid(result.getTotalPaid().add(itemOriginal));
            if (isCash) {
                result.setCashPaid(result.getCashPaid().add(itemOriginal));
            } else {
                result.setNonCashPaid(result.getNonCashPaid().add(itemOriginal));
            }
        }

        if (result.getTotalPaid().compareTo(finalPayAmount) < 0) {
            throw new BaseException(String.format("实付金额不足。本单应收: %s，实收: %s", finalPayAmount, result.getTotalPaid()));
        }
        if (result.getNonCashPaid().compareTo(finalPayAmount) > 0) {
            throw new BaseException("【风控拦截】非现金支付总额超过了应付总额，严禁套现！");
        }

        // 🌟 2. 计算总找零
        result.setChangeAmount(result.getTotalPaid().subtract(finalPayAmount));
        result.setNetReceived(result.getTotalPaid().subtract(result.getChangeAmount()));

        // 🌟 3. 将找零分摊给现金支付项
        BigDecimal remainChange = result.getChangeAmount();
        for (NormalizedPaymentResult.StandardPayItem item : result.getValidItems()) {
            if (item.isCash() && remainChange.compareTo(BigDecimal.ZERO) > 0) {
                if (item.getOriginalAmount().compareTo(remainChange) >= 0) {
                    item.setChangeAmount(remainChange);
                    item.setNetAmount(item.getOriginalAmount().subtract(remainChange));
                    remainChange = BigDecimal.ZERO;
                } else {
                    item.setChangeAmount(item.getOriginalAmount());
                    item.setNetAmount(BigDecimal.ZERO);
                    remainChange = remainChange.subtract(item.getOriginalAmount());
                }
            }
            item.setNetAmount(item.getNetAmount().setScale(2, RoundingMode.HALF_UP));
            item.setOriginalAmount(item.getOriginalAmount().setScale(2, RoundingMode.HALF_UP));
            item.setChangeAmount(item.getChangeAmount().setScale(2, RoundingMode.HALF_UP));
        }

        result.setTotalPaid(result.getTotalPaid().setScale(2, RoundingMode.HALF_UP));
        result.setCashPaid(result.getCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setNonCashPaid(result.getNonCashPaid().setScale(2, RoundingMode.HALF_UP));
        result.setChangeAmount(result.getChangeAmount().setScale(2, RoundingMode.HALF_UP));
        result.setNetReceived(result.getNetReceived().setScale(2, RoundingMode.HALF_UP));

        return result;
    }
}