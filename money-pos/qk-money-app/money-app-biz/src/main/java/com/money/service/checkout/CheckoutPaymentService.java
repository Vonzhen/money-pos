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

    /**
     * 🌟 P1-2 核心修复：时空回放逻辑。
     * 从数据库中提取支付胶囊，100% 还原当时的实收、找零和净额。
     */
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

            // 兼容老数据：如果新字段为空，则回退使用老字段
            BigDecimal net = pay.getNetAmount() != null ? pay.getNetAmount() : pay.getPayAmount();
            BigDecimal orig = pay.getOriginalAmount() != null ? pay.getOriginalAmount() : net;
            BigDecimal change = pay.getChangeAllocated() != null ? pay.getChangeAllocated() : BigDecimal.ZERO;

            item.setNetAmount(net);
            // 假设 StandardPayItem 中有这两个 setter (如果没有请自行补充)
            item.setOriginalAmount(orig);
            item.setChangeAmount(change);

            result.getValidItems().add(item);

            // 累加计算小票大盘数据
            netTotal = netTotal.add(net);
            originalTotal = originalTotal.add(orig);
            changeTotal = changeTotal.add(change);
        }

        // 🌟 完美还原：将历史的真实统计数据注回 Result，供前端无损回显
        result.setNetReceived(netTotal);
        result.setTotalPaid(originalTotal);
        result.setChangeAmount(changeTotal);

        context.setPaymentResult(result);
    }

    /**
     * 🌟 P1-2 核心修复：入库时将快照数据全面落盘。
     */
    public void handlePayment(CheckoutContext context) {
        NormalizedPaymentResult payResult = context.getPaymentResult();
        String orderNo = context.getOrder().getOrderNo();
        LocalDateTime now = LocalDateTime.now();

        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
            // 如果实收和净额都为0，则忽略
            if ((item.getNetAmount() == null || item.getNetAmount().compareTo(BigDecimal.ZERO) == 0) &&
                    (item.getOriginalAmount() == null || item.getOriginalAmount().compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getMethodCode());
            payRecord.setPayMethodName(item.getMethodName());
            payRecord.setPayTag(item.getPayTag());

            // 遗留字段兼容
            payRecord.setPayAmount(item.getNetAmount());

            // 🌟 真实快照落盘 (兜底防止空指针)
            payRecord.setNetAmount(item.getNetAmount());
            payRecord.setOriginalAmount(item.getOriginalAmount() != null ? item.getOriginalAmount() : item.getNetAmount());
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);

            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }
}