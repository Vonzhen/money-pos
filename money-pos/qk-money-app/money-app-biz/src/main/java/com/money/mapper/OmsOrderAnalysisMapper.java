package com.money.mapper;

import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.BrandSalesVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.GoodsSalesRankVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO; // 🌟 导入刚才定义的内部类
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 经营分析集市 Mapper (OLAP)
 * 职责：专职大盘数据钻取，已全局统一金额与状态口径
 */
@Mapper
public interface OmsOrderAnalysisMapper {

    @Select("SELECT " +
            "  CASE " +
            "    WHEN #{dimension} = 'MONTHLY' THEN DATE_FORMAT(create_time, '%Y-%m') " +
            "    WHEN #{dimension} = 'WEEKLY' THEN DATE_FORMAT(create_time, '%x-W%v') " +
            "    ELSE DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "  END AS period, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) AS netSalesAmount, " +
            "  SUM(IFNULL(cost_amount, 0)) AS costAmount, " +
            "  SUM((SELECT SUM(IFNULL(quantity, 0) - IFNULL(return_quantity, 0)) FROM oms_order_detail d WHERE d.order_no = o.order_no)) AS goodsCount " +
            "FROM oms_order o " +
            "WHERE status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "  AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY period ORDER BY period ASC")
    List<AnalysisAtomicDataDTO> getPeriodAtomicStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("dimension") String dimension);

    @Select("SELECT " +
            "  d.goods_name AS goodsName, " +
            "  SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING salesQty > 0 " +
            "ORDER BY salesQty DESC LIMIT 50")
    List<GoodsSalesRankVO> getTopGoodsRank(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  IFNULL(b.name, '无品牌/未知') AS brandName, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "LEFT JOIN gms_brand b ON d.brand_id = b.id " +
            "WHERE o.status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.brand_id, brandName " +
            "HAVING salesAmount > 0 " +
            "ORDER BY salesAmount DESC")
    List<BrandSalesVO> getBrandSalesDistribution(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT '满减券' AS ruleType, IFNULL(remark, '通用满减活动') AS ruleName, COUNT(id) AS usedCount, SUM(IFNULL(use_voucher_amount, 0)) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, 0)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND IFNULL(use_voucher_amount, 0) > 0 GROUP BY ruleName " +
            "UNION ALL " +
            "SELECT '会员资产' AS ruleType, '会员专属券核销' AS ruleName, COUNT(id) AS usedCount, SUM(IFNULL(coupon_amount, 0)) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, 0)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND IFNULL(coupon_amount, 0) > 0")
    List<MarketingRoiVO> getMarketingRoiStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // ==========================================
    // 🌟 新增：商品分类销售占比分析
    // ==========================================
    @Select("SELECT " +
            "  IFNULL(c.name, '未分类') AS categoryName, " +
            "  SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "LEFT JOIN gms_goods_category c ON d.category_id = c.id " +
            "WHERE o.status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.category_id, categoryName " +
            "HAVING salesQty > 0 " +
            "ORDER BY salesAmount DESC")
    List<CategorySalesVO> getCategorySalesDistribution(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}