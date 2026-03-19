package com.money.dto.OmsOrder;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 5.x 销售领域全量数据传输对象
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
    }

    @Data
    public static class GoodsSalesRankVO {
        private String goodsName;
        private Integer salesQty;
        private BigDecimal salesAmount;
        public GoodsSalesRankVO(String goodsName, Integer salesQty, BigDecimal salesAmount) {
            this.goodsName = goodsName; this.salesQty = salesQty; this.salesAmount = salesAmount;
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

    // ==========================================
    // 🌟 8.2 核心新增：办事罗盘（24小时客流分析）数据体
    // ==========================================
    @Data
    public static class HourlyTrafficVO {
        private Integer hour;               // 几点 (0-23)
        private java.math.BigDecimal avgOrderCount;   // 历史平均单量
        private java.math.BigDecimal avgSalesAmount;  // 历史平均产出
        private String suggestion;          // 建议："OUT" (可外出), "STAY" (需留守)
    }

    // 🌟 宏观潮汐罗盘数据体 (按周/按月通用)
    @Data
    public static class TimeTrafficVO {
        private Integer timeKey; // 星期几(1-7) 或 月份几号(1-31)
        private java.math.BigDecimal avgOrderCount;
        private java.math.BigDecimal avgSalesAmount;
    }
}