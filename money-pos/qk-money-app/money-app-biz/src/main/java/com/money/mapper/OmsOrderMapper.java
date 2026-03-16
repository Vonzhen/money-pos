package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.BrandSalesVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.GoodsSalesRankVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.entity.OmsOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🌟 订单 Mapper (V7.7 终极排雷版 - 纯净 Native SQL，彻底杜绝解析异常)
 */
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    // ==========================================
    // 1. 退款原子武器
    // ==========================================

    @Update("UPDATE oms_order SET " +
            "final_sales_amount = IFNULL(final_sales_amount, pay_amount) - #{refundSales}, " +
            "cost_amount = IFNULL(cost_amount, 0) - #{refundCost}, " +
            "coupon_amount = IFNULL(coupon_amount, 0) - #{refundCoupon}, " +
            "status = #{status} " +
            "WHERE order_no = #{orderNo}")
    int applyPartialRefund(@Param("orderNo") String orderNo,
                           @Param("refundSales") BigDecimal refundSales,
                           @Param("refundCost") BigDecimal refundCost,
                           @Param("refundCoupon") BigDecimal refundCoupon,
                           @Param("status") String status);

    @Update("UPDATE oms_order SET " +
            "final_sales_amount = 0, cost_amount = 0, coupon_amount = 0, use_voucher_amount = 0, status = #{status} " +
            "WHERE order_no = #{orderNo}")
    int updateRefundStatusToFull(@Param("orderNo") String orderNo, @Param("status") String status);

    @Update("UPDATE oms_order SET status = 'REFUNDED' " +
            "WHERE order_no = #{orderNo} " +
            "AND (SELECT SUM(quantity) FROM oms_order_detail WHERE order_no = #{orderNo}) = " +
            "    (SELECT SUM(IFNULL(return_quantity, 0)) FROM oms_order_detail WHERE order_no = #{orderNo})")
    void checkAndUpgradeToFullRefund(@Param("orderNo") String orderNo);

    // ==========================================
    // 2. 经营分析看板 (100% 纯 SQL，告别 XML 解析雷区)
    // ==========================================

    @Select("SELECT " +
            "  CASE " +
            "    WHEN #{dimension} = 'MONTHLY' THEN DATE_FORMAT(create_time, '%Y-%m') " +
            "    WHEN #{dimension} = 'WEEKLY' THEN DATE_FORMAT(create_time, '%x-W%v') " +
            "    ELSE DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "  END AS period, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(IFNULL(final_sales_amount, pay_amount)) AS netSalesAmount, " +
            "  SUM(IFNULL(cost_amount, 0)) AS costAmount, " +
            "  (SELECT SUM(quantity - IFNULL(return_quantity, 0)) FROM oms_order_detail WHERE order_no IN " +
            "    (SELECT order_no FROM oms_order o2 WHERE DATE(o2.create_time) = DATE(o.create_time) " +
            "     AND o2.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED'))) AS goodsCount " +
            "FROM oms_order o " +
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "  AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY period ORDER BY period ASC")
    List<AnalysisAtomicDataDTO> getPeriodAtomicStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("dimension") String dimension);

    @Select("SELECT " +
            "  d.goods_name AS goodsName, " +
            "  SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING SUM(d.quantity - IFNULL(d.return_quantity, 0)) > 0 " +
            "ORDER BY salesQty DESC LIMIT 50")
    List<GoodsSalesRankVO> getTopGoodsRank(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  IFNULL(b.name, '无品牌/未知') AS brandName, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "LEFT JOIN gms_brand b ON d.brand_id = b.id " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.brand_id, brandName " +
            "HAVING SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) > 0 " +
            "ORDER BY salesAmount DESC")
    List<BrandSalesVO> getBrandSalesDistribution(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT '满减券' AS ruleType, IFNULL(remark, '通用满减活动') AS ruleName, COUNT(id) AS usedCount, SUM(use_voucher_amount) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, pay_amount)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND use_voucher_amount > 0 GROUP BY ruleName " +
            "UNION ALL " +
            "SELECT '会员资产' AS ruleType, '会员专属券核销' AS ruleName, COUNT(id) AS usedCount, SUM(coupon_amount) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, pay_amount)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND coupon_amount > 0")
    List<MarketingRoiVO> getMarketingRoiStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // ==========================================
    // 3. 审计与风控巡检 (纯 SQL 动态适配)
    // ==========================================

    @Select("SELECT " +
            "  order_no AS orderNo, goods_name AS goodsName, create_time AS createTime, " +
            "  IFNULL(sale_price, 0) AS salePrice, IFNULL(goods_price, 0) AS goodsPrice, IFNULL(purchase_price, 0) AS purchasePrice, " +
            "  (IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) AS unitProfit " +
            "FROM oms_order_detail " +
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "  AND (IFNULL(#{orderNo}, '') = '' OR order_no = #{orderNo}) " +
            "ORDER BY create_time DESC")
    Page<ProfitAuditVO> getProfitAuditPage(Page<?> page, @Param("orderNo") String orderNo);

    @Select("SELECT " +
            "  create_by AS cashierName, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(manual_discount_amount) AS manualDiscountAmount, " +
            "  SUM(CASE WHEN status IN ('PARTIAL_REFUNDED', 'REFUNDED') THEN 1 ELSE 0 END) AS refundCount " +
            "FROM oms_order " +
            "WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY create_by")
    List<Map<String, Object>> getCashierRiskSummary(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  order_no AS orderNo, " +
            "  DATE_FORMAT(create_time, '%m-%d %H:%i') AS createTime, " +
            "  create_by AS cashier, " +
            "  pay_amount AS payAmount, " +
            "  cost_amount AS costAmount, " +
            "  (IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) AS profit, " +
            "  CASE " +
            "    WHEN (pay_amount >= 10 AND (cost_amount IS NULL OR cost_amount <= 0)) THEN '缺失成本预警' " +
            "    WHEN ((IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) < 0 AND pay_amount > 0) THEN '倒挂亏损交易' " +
            "    WHEN manual_discount_amount > 50 THEN '大额手工放水' " +
            "    ELSE '其他风险' " +
            "  END AS riskType " +
            "FROM oms_order " +
            "WHERE (create_time >= #{startTime} AND create_time <= #{endTime}) " +
            "  AND ( " +
            "    (pay_amount >= 10 AND (cost_amount IS NULL OR cost_amount <= 0)) " +
            "    OR ((IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) < 0 AND pay_amount > 0) " +
            "    OR manual_discount_amount > 50 " +
            "  ) " +
            "ORDER BY profit ASC LIMIT 50")
    List<Map<String, Object>> getAbnormalOrderList(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}