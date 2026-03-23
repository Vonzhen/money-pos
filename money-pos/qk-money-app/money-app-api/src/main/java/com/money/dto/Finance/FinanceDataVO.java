package com.money.dto.Finance;

import io.swagger.v3.oas.annotations.media.Schema;
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

        private Map<String, List<BigDecimal>> dynamicTrendMap;
        @Schema(description = "近7天售后退款专线")
        private List<BigDecimal> trendRefund;
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

    // ==========================================
    // 🌟 核心修复区：品牌贡献度实体类
    // ==========================================
    @Data
    public static class BrandContributionVO {
        private String brandName;
        private BigDecimal revenue;
        private BigDecimal couponConsumption;

        // 🌟 修复 1：必须提供一个无参构造！让 MyBatis 可以先建个空对象，不至于因为参数不够而越界爆炸。
        public BrandContributionVO() {
        }

        // 保留原有的全参构造，防止系统中其他手动 new 这个对象的地方报错。
        public BrandContributionVO(String brandName, BigDecimal revenue, BigDecimal couponConsumption) {
            this.brandName = brandName; this.revenue = revenue; this.couponConsumption = couponConsumption;
        }

        // 🌟 修复 2：偷天换日！SQL 查出来的列名叫 `brandSales`，MyBatis 会来找 `setBrandSales` 方法。
        // 我们在这里拦截它，并把值悄悄赋给前端真正需要的 `revenue` 字段！
        public void setBrandSales(BigDecimal brandSales) {
            this.revenue = brandSales;
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

    @Data
    public static class RiskControlVO {
        private Integer abnormalOrderCount;
        private BigDecimal totalLossAmount;
        private BigDecimal totalManualDiscount;
        private Integer totalRefundCount;

        private List<CashierRiskVO> cashierRiskList;
        private List<AbnormalOrderVO> recentAbnormalOrders;
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

    @Data
    public static class AssetDashboardVO {
        private BigDecimal todayRealCash;
        private BigDecimal todayWaivedAmount;
        private BigDecimal todayAssetDeduct;

        private BigDecimal principalRatio;
        private BigDecimal giftRatio;
    }
}