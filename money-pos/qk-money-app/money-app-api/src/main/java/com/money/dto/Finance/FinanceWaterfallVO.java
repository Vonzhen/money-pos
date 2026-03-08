package com.money.dto.Finance;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FinanceWaterfallVO {
    private String date; // 日期，例如 "2026-03-08"
    private BigDecimal totalAmount; // 应收总额
    private BigDecimal couponAmount; // 会员券总额
    private BigDecimal voucherAmount; // 满减券总额
    private BigDecimal manualDiscountAmount; // 整单优惠总额
    private BigDecimal payAmount; // 实付总额
    private BigDecimal refundAmount; // 退款总额
    private BigDecimal netIncome; // 净收总额
}