package com.money.dto.OmsOrder;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

public class OmsSalesDataVO {

    @Data
    public static class SalesDashboardVO {
        // 5.3 经营业绩核心指标
        private BigDecimal totalSalesAmount; // 总销售额(实付)
        private Integer totalOrderCount;     // 总订单数
        private Integer totalGoodsCount;     // 售出商品总件数
        private BigDecimal avgOrderValue;    // 客单价 (总销售额/总单数)

        // 5.2 商品分析与品牌分布
        private List<GoodsSalesRankVO> topGoodsRanking;
        private List<BrandSalesVO> brandDistribution;

        // 5.3 业绩趋势双轨
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

    // ... [上方原有的 SalesDashboardVO 等保留] ...

    // 🌟 5.3 核心新增：经营业绩汇总报表 VO
    @Data
    public static class PerformanceReportVO {
        private String period; // 日期区间 (如 2024-05-20, 或 2024-W21, 或 2024-05)
        private Integer orderCount; // 单量
        private Integer goodsCount; // 动销件数
        private BigDecimal salesAmount; // 营业额
        private BigDecimal avgOrderValue; // 客单价

        public PerformanceReportVO(String period, Integer orderCount, Integer goodsCount, BigDecimal salesAmount, BigDecimal avgOrderValue) {
            this.period = period; this.orderCount = orderCount; this.goodsCount = goodsCount; this.salesAmount = salesAmount; this.avgOrderValue = avgOrderValue;
        }
    }
}