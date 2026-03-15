package com.money.service.checkout;

import com.money.dto.pos.NormalizedPaymentResult;
import com.money.entity.OmsOrderPay;
import com.money.mapper.OmsOrderPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 🌟 结算流水线第六关：出纳员
 * 负责记录每一笔资金真实的流入渠道（支付流水表落库）。
 */
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    // 这里引入的是 Mapper，在 com.money.mapper 包下，路径是标准的
    private final OmsOrderPayMapper omsOrderPayMapper;

    public void handlePayment(CheckoutContext context) {
        NormalizedPaymentResult payResult = context.getPaymentResult();
        String orderNo = context.getOrder().getOrderNo();
        LocalDateTime now = LocalDateTime.now();

        // 遍历清洗好的标准支付明细
        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
            // 如果净收金额为0（比如给了100现金，找零100，相当于没收钱），就不记流水
            if (item.getNetAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getMethodCode());
            payRecord.setPayMethodName(item.getMethodName());
            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }
}