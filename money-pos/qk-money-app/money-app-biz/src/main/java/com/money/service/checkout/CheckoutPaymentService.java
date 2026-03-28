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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 结算流水线第六关：出纳员 (增加真实流水回显能力)
 */
@Slf4j // 🌟 补全了日志注解
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;
    // 🌟 注入字典服务
    private final SysDictDetailService sysDictDetailService;

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
            // 摒弃前端传入的 Name，采用后台动态字典安全查询匹配！
            // ==========================================
            payRecord.setPayMethodName(getSafeMethodName(item.getMethodCode()));

            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setNetAmount(item.getNetAmount());
            payRecord.setOriginalAmount(item.getOriginalAmount() != null ? item.getOriginalAmount() : item.getNetAmount());
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }

    // 🌟 终极安全映射引擎：查字典，既绝对零信任前端，又保留系统的动态配置能力！
    private String getSafeMethodName(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "未知支付";
        }

        try {
            // ⚠️ 注意：这里的 "pay_method" 请确保是您在后台实际配置的支付方式【字典类型编码】
            List<SysDictDetail> dictList = sysDictDetailService.listByDict("pay_method");

            if (dictList != null) {
                for (SysDictDetail detail : dictList) {
                    // 🌟 核心修复：完美对接您的实体类！用 getValue() 对比，用 getCnDesc() 拿中文名
                    if (code.equalsIgnoreCase(detail.getValue())) {
                        return detail.getCnDesc();
                    }
                }
            }
        } catch (Exception e) {
            log.error("💥 动态匹配支付方式字典失败，降级到安全兜底模式。代码: {}", code, e);
        }

        // 🌟 核心兜底：如果在字典里没查到（非法或未知的 Code），使用代码兜底防黑客！
        return "其他渠道(" + code + ")";
    }
}