package com.money.dto.Finance;

import lombok.Data;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FinanceDataVO {

    @Data
    public static class FinanceDashboardVO {
        private BigDecimal totalAmount;
        private BigDecimal totalDiscount;
        private BigDecimal payAmount;
        private BigDecimal refundAmount;
        private BigDecimal netIncome;
        private BigDecimal grossProfit;
        private BigDecimal externalIncome;
        private BigDecimal totalDebt;

        private List<PayPieData> payBreakdown;
        private List<String> trendDates;
        private List<BigDecimal> trendScan;
        private List<BigDecimal> trendCash;
        private List<BigDecimal> trendRecharge;
        private List<BigDecimal> trendTotal;

        // 🌟 修复：把这个字段挪到了 FinanceDashboardVO 内部！
        private Map<String, List<BigDecimal>> dynamicTrendMap;
    }

    @Data
    public static class PayPieData {
        private String name;
        private BigDecimal value;
        public PayPieData(String name, BigDecimal value) { this.name = name; this.value = value; }
    }

    @Data
    public static class ProfitRankVO {
        private String goodsName;
        private Integer totalQuantity;
        private BigDecimal totalSales;
        private BigDecimal totalProfit;
        public ProfitRankVO(String goodsName, Integer totalQuantity, BigDecimal totalSales, BigDecimal totalProfit) {
            this.goodsName = goodsName; this.totalQuantity = totalQuantity; this.totalSales = totalSales; this.totalProfit = totalProfit;
        }
    }

    @Data
    public static class ShiftHandoverVO {
        private String shiftStartTime;
        private String shiftEndTime;
        private String cashierName;
        private BigDecimal cashPay;
        private BigDecimal scanPay;
        private BigDecimal expectedTotalIncome;
        private BigDecimal balancePay;
        private BigDecimal memberCouponPay;
        private BigDecimal voucherDiscount;
        private Integer voucherCount;
        private BigDecimal manualDiscount;
        private List<BrandContributionVO> brandMatrix;
    }

    @Data
    public static class BrandContributionVO {
        private String brandName;
        private BigDecimal revenue;
        private BigDecimal couponConsumption;
        public BrandContributionVO(String brandName, BigDecimal revenue, BigDecimal couponConsumption) {
            this.brandName = brandName; this.revenue = revenue; this.couponConsumption = couponConsumption;
        }
    }

    @Data
    public static class CampaignReviewVO {
        private String ruleName;
        private Integer usedCount;
        private BigDecimal totalDiscountGived;
        private BigDecimal totalRevenueBrought;
        private BigDecimal roiMultiplier;
        private Set<String> trackedOrderNos = new HashSet<>();
        public CampaignReviewVO(String ruleName, Integer usedCount, BigDecimal totalDiscountGived, BigDecimal totalRevenueBrought, BigDecimal roiMultiplier) {
            this.ruleName = ruleName; this.usedCount = usedCount; this.totalDiscountGived = totalDiscountGived; this.totalRevenueBrought = totalRevenueBrought; this.roiMultiplier = roiMultiplier;
        }
    }

    @Data
    public static class ChannelMixAnalysisVO {
        private List<String> trendDates;
        private List<BigDecimal> scanList;
        private List<BigDecimal> cashList;
        private List<BigDecimal> balanceList;
        private List<BigDecimal> couponList;
        private List<BigDecimal> voucherList;
        private List<PayPieData> pieData;
    }

    // ==========================================
    // 🌟 6.6 核心新增：风控雷达数据结构
    // ==========================================
    @Data
    public static class RiskControlVO {
        private Integer abnormalOrderCount; // 异常单数
        private BigDecimal totalLossAmount; // 直接损失金额
        private BigDecimal totalManualDiscount; // 手工放水总额
        private Integer totalRefundCount; // 发生退款的单据数

        private List<CashierRiskVO> cashierRiskList; // 收银员风控黑榜
        private List<AbnormalOrderVO> recentAbnormalOrders; // 高危异常订单列表
    }

    @Data
    public static class CashierRiskVO {
        private String cashierName;
        private Integer orderCount;
        private BigDecimal manualDiscountAmount;
        private Integer refundCount;
        public CashierRiskVO(String cashierName, Integer orderCount, BigDecimal manualDiscountAmount, Integer refundCount) {
            this.cashierName = cashierName; this.orderCount = orderCount; this.manualDiscountAmount = manualDiscountAmount; this.refundCount = refundCount;
        }
    }

    @Data
    public static class AbnormalOrderVO {
        private String orderNo;
        private String createTime;
        private String cashier;
        private BigDecimal payAmount;
        private BigDecimal costAmount;
        private BigDecimal profit;
        private String riskType;
    }

    // ==========================================
    // 🌟 8.1 核心新增：资产驾驶舱数据结构
    // ==========================================
    @Data
    public static class AssetDashboardVO {
        private BigDecimal todayRealCash;      // 今日实收现金 (final_pay_amount)
        private BigDecimal todayWaivedAmount;  // 今日店铺免收 (waived_coupon_amount)
        private BigDecimal todayAssetDeduct;   // 今日会员核销 (actual_coupon_deduct)

        private BigDecimal principalRatio;     // 本金消耗占比 (%)
        private BigDecimal giftRatio;          // 赠送金消耗占比 (%)
    }
}