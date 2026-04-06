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
 * 🌟 结算流水线第六关：出纳员 (支付方式大一统升级版)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;
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
            payRecord.setPayTag(item.getPayTag());

            // ==========================================
            // 🌟 核心升级：调用双层字典解析引擎，传入 Code 和 Tag
            // ==========================================
            payRecord.setPayMethodName(getSafeMethodName(item.getMethodCode(), item.getPayTag()));

            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setNetAmount(item.getNetAmount());
            payRecord.setOriginalAmount(item.getOriginalAmount() != null ? item.getOriginalAmount() : item.getNetAmount());
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }

    // 🌟 全系统统一的双层支付翻译官：完全放权给数据库字典！
    private String getSafeMethodName(String methodCode, String payTag) {
        try {
            // 1. 优先查子标签字典 (例如: 微信支付, 抖音支付)
            if (StringUtils.hasText(payTag)) {
                List<SysDictDetail> subList = sysDictDetailService.listByDict("paySubTag");
                if (subList != null) {
                    for (SysDictDetail detail : subList) {
                        if (payTag.equalsIgnoreCase(detail.getValue())) {
                            return detail.getCnDesc();
                        }
                    }
                }
            }

            // 2. 其次查主通道字典 (例如: 现金, 余额, 聚合扫码)
            if (StringUtils.hasText(methodCode)) {
                // 🌟 修正错配：统一使用数据库真实存在的 'pos_payment_method'
                List<SysDictDetail> mainList = sysDictDetailService.listByDict("pos_payment_method");
                if (mainList != null) {
                    for (SysDictDetail detail : mainList) {
                        if (methodCode.equalsIgnoreCase(detail.getValue())) {
                            return detail.getCnDesc();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("💥 动态匹配支付方式字典失败，降级到安全兜底模式。Code:{}, Tag:{}", methodCode, payTag, e);
        }

        // 3. 终极兜底：只有当数据库故意漏配，或者遭受非法 Code 注入时，才会走到这里
        String code = StringUtils.hasText(payTag) ? payTag : methodCode;
        return "其他渠道(" + code + ")";
    }
}