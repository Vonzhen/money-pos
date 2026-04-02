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

        @Schema(description = "实际核销会员券")
        private BigDecimal actualCouponDeduct;
        @Schema(description = "店铺免券让利")
        private BigDecimal waivedCouponAmount;
        @Schema(description = "满减活动抵扣")
        private BigDecimal voucherAmount;
        @Schema(description = "手工整单优惠")
        private BigDecimal manualDiscountAmount;

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

        // 🌟 极致细节：扫码支付通道细分列表
        private List<PayPieData> scanPayBreakdown;

        private BigDecimal expectedTotalIncome;
        private BigDecimal balancePay;

        private BigDecimal memberCouponPay;
        private BigDecimal voucherDiscount;
        private Integer voucherCount;
        private BigDecimal manualDiscount;
        private BigDecimal waivedCouponAmount;

        private BigDecimal refundAmount;
        private BigDecimal netIncome;

        private List<BrandContributionVO> brandMatrix;
    }

    @Data
    public static class BrandContributionVO {
        private String brandName;
        private BigDecimal revenue;
        private BigDecimal couponConsumption;

        public BrandContributionVO() {}
        public BrandContributionVO(String brandName, BigDecimal revenue, BigDecimal couponConsumption) {
            this.brandName = brandName; this.revenue = revenue; this.couponConsumption = couponConsumption;
        }
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