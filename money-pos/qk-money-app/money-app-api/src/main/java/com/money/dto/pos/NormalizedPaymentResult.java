package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class NormalizedPaymentResult {
    private BigDecimal totalPaid = BigDecimal.ZERO;     // 顾客总递交金额
    private BigDecimal cashPaid = BigDecimal.ZERO;      // 其中现金部分
    private BigDecimal nonCashPaid = BigDecimal.ZERO;   // 其中非现金部分
    private BigDecimal changeAmount = BigDecimal.ZERO;  // 核算出的找零
    private BigDecimal netReceived = BigDecimal.ZERO;   // 财务净入账 = totalPaid - changeAmount

    @Data
    public static class StandardPayItem {
        private String methodCode;
        private String methodName;
        private String payTag;
        private boolean isCash;
        private BigDecimal originalAmount; // 原始填入
        private BigDecimal netAmount;      // 扣除找零后的净额
    }

    private List<StandardPayItem> validItems = new ArrayList<>();
}