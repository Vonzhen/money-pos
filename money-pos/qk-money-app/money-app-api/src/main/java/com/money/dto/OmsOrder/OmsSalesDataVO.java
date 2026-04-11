package com.money.dto.OmsOrder;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 5.x 销售领域全量数据传输对象 (大一统趋势升级版)
 */
public class OmsSalesDataVO {

    @Data
    public static class SalesDashboardVO {
        private BigDecimal totalSalesAmount;
        private Integer totalOrderCount;
        private Integer totalGoodsCount;
        private BigDecimal avgOrderValue;

        private List<GoodsSalesRankVO> topGoodsRanking;
        private List<BrandSalesVO> brandDistribution;

        private List<String> trendDates;
        private List<BigDecimal> trendSales;
        private List<Integer> trendOrders;

        private List<BigDecimal> trendAsp;
        private MemberTrendVO memberTrend;
    }

    @Data
    public static class MemberTrendVO {
        private List<String> dates;
        private List<BigDecimal> memberSales;
        private List<BigDecimal> guestSales;
        private List<BigDecimal> memberAsp;
        private List<BigDecimal> guestAsp;
    }

    @Data
    public static class DailyMemberStatDTO {
        private String dateStr;
        private Integer isMember;
        private Integer orderCount;
        private BigDecimal salesAmount;
    }

    @Data
    public static class GoodsTrendVO {
        private Long goodsId;
        private String goodsName;
        private List<Integer> trendSalesQty;
    }

    @Data
    public static class DailyGoodsStatDTO {
        private String dateStr;
        private Long goodsId;
        private String goodsName;
        private Integer salesQty;
    }

    @Data
    public static class GoodsSalesRankVO {
        private Long goodsId;
        private String goodsName;
        private Integer salesQty;
        private BigDecimal salesAmount;

        public GoodsSalesRankVO(Long goodsId, String goodsName, Integer salesQty, BigDecimal salesAmount) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.salesQty = salesQty;
            this.salesAmount = salesAmount;
        }
    }

    @Data
    public static class BrandSalesVO {
        private String brandName;
        private BigDecimal salesAmount;
        public BrandSalesVO(String brandName, BigDecimal salesAmount) {
            this.brandName = brandName; this.salesAmount = salesAmount;
        }
    }

    @Data
    public static class CategorySalesVO {
        private String categoryName;
        private Integer salesQty;
        private BigDecimal salesAmount;
    }

    @Data
    public static class PerformanceReportVO {
        private String period;
        private Integer orderCount;
        private Integer goodsCount;
        private BigDecimal salesAmount;
        private BigDecimal avgOrderValue;

        public PerformanceReportVO(String period, Integer orderCount, Integer goodsCount, BigDecimal salesAmount, BigDecimal avgOrderValue) {
            this.period = period; this.orderCount = orderCount; this.goodsCount = goodsCount; this.salesAmount = salesAmount; this.avgOrderValue = avgOrderValue;
        }
    }

    @Data
    public static class MarketingRoiVO {
        private String ruleName;
        private String ruleType;
        private Integer usedCount;
        private BigDecimal totalDiscountGived;
        private BigDecimal totalRevenueBrought;
        private BigDecimal roiMultiplier;
        private BigDecimal avgOrderValue;
    }

    @Data
    public static class HourlyTrafficVO {
        private Integer hour;
        private java.math.BigDecimal avgOrderCount;
        private java.math.BigDecimal avgSalesAmount;

        // 🌟 核心新增：保留原始总数据，彻底剥夺前端计算权
        private java.math.BigDecimal totalOrderCount;
        private java.math.BigDecimal totalSalesAmount;
        private Integer sampleDays; // 后端计算出的确切采样天数，给前端展示文案用

        private String suggestion;
    }

    @Data
    public static class TimeTrafficVO {
        private Integer timeKey;
        private java.math.BigDecimal avgOrderCount;
        private java.math.BigDecimal avgSalesAmount;

        // 🌟 核心新增：保留原始总数据
        private java.math.BigDecimal totalOrderCount;
        private java.math.BigDecimal totalSalesAmount;
        private Double sampleDays; // 采样周期倍数
    }
}