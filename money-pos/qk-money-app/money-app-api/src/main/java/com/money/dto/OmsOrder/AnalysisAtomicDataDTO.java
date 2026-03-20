package com.money.dto.OmsOrder;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 第 2 层：指标口径层 (原子统计结果与派生公式)
 */
@Data
public class AnalysisAtomicDataDTO {
    // === 数据库输出的原子指标 (Layer 1 赋予) ===
    private String period; // 聚合周期维度 (日期/周/月)
    private Integer orderCount = 0; // 结单数
    private Integer goodsCount = 0; // 出库净件数 (买入-退货)
    private BigDecimal payAmount = BigDecimal.ZERO; // 原始实付额
    private BigDecimal netSalesAmount = BigDecimal.ZERO; // 净销售额 (实付-退款)
    private BigDecimal costAmount = BigDecimal.ZERO; // 净成本

    // === 业务派生指标 (Layer 2 固化口径，防止乱算) ===

    /**
     * 派生：净利润 = 净销售额 - 净成本
     */
    public BigDecimal getProfit() {
        return (netSalesAmount == null ? BigDecimal.ZERO : netSalesAmount)
                .subtract(costAmount == null ? BigDecimal.ZERO : costAmount);
    }

    /**
     * 派生：客单价 (ASP) = 净销售额 / 订单数
     */
    public BigDecimal getAsp() {
        if (orderCount == null || orderCount == 0) return BigDecimal.ZERO;
        BigDecimal net = netSalesAmount == null ? BigDecimal.ZERO : netSalesAmount;
        return net.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP);
    }
}