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
 * 🌟 结算流水线第六关：出纳员 (增加流水回显能力)
 */
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;

    /**
     * 🌟 新增：重试找回逻辑。把之前存的支付流水找回来拼成结果。
     */
    public void loadExistingPayments(CheckoutContext context) {
        String orderNo = context.getOrder().getOrderNo();

        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(
                new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo)
        );

        NormalizedPaymentResult result = new NormalizedPaymentResult();
        BigDecimal netTotal = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            NormalizedPaymentResult.StandardPayItem item = new NormalizedPaymentResult.StandardPayItem();
            item.setMethodCode(pay.getPayMethodCode());
            item.setMethodName(pay.getPayMethodName());
            item.setPayTag(pay.getPayTag());
            item.setNetAmount(pay.getPayAmount());
            result.getValidItems().add(item);

            netTotal = netTotal.add(pay.getPayAmount());
        }

        // 简化处理：重试时不再精确还原“找零”，只保证净入账（netReceived）和总应付对齐即可
        result.setNetReceived(netTotal);
        result.setTotalPaid(netTotal);
        result.setChangeAmount(BigDecimal.ZERO);

        context.setPaymentResult(result);
    }

    public void handlePayment(CheckoutContext context) {
        // ... 原有写库逻辑不变 ...
        NormalizedPaymentResult payResult = context.getPaymentResult();
        String orderNo = context.getOrder().getOrderNo();
        LocalDateTime now = LocalDateTime.now();

        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
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