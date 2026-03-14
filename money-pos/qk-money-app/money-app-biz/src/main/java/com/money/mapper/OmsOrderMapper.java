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

import java.time.LocalDateTime;
import java.util.List;

public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    // --- 1. 真实损益毛利审计 (页面级：支持查单号、看异常) ---
    @Select("<script>" +
            "SELECT " +
            "  order_no AS orderNo, " +
            "  goods_name AS goodsName, " +
            "  create_time AS createTime, " +
            "  IFNULL(sale_price, 0) AS salePrice, " +
            "  IFNULL(goods_price, 0) AS goodsPrice, " +
            "  IFNULL(purchase_price, 0) AS purchasePrice, " +
            "  (IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) AS unitProfit, " +
            "  CASE " +
            "    WHEN IFNULL(goods_price, 0) &gt; 0 THEN ((IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) / IFNULL(goods_price, 0)) " +
            "    ELSE 0 " +
            "  END AS profitMargin, " +
            "  IF(purchase_price IS NULL OR purchase_price &lt;= 0, 1, 0) AS isMissingCost, " +
            "  IF((IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) &lt; 0, 1, 0) AS isNegativeMargin " +
            "FROM oms_order_detail " +
            "WHERE status IN ('PAID', 'PARTIAL', 'REFUNDED') " +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no = #{orderNo} </if>" +
            "<if test='anomalyOnly != null and anomalyOnly == true'> AND (purchase_price IS NULL OR purchase_price &lt;= 0 OR (goods_price - purchase_price) &lt; 0) </if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    Page<ProfitAuditVO> getProfitAuditPage(
            Page<?> page,
            @Param("orderNo") String orderNo,
            @Param("anomalyOnly") Boolean anomalyOnly);


    // --- 2. 🌟 神级对齐 SQL：时间轴原子数据聚合 (按日/周/月) ---
    // 解决：主表成本为0的漏洞，直接通过子查询从明细表推算绝对准确的净额和成本
    @Select("<script>" +
            "SELECT " +
            "  <choose>" +
            "    <when test=\"dimension == 'MONTHLY'\">DATE_FORMAT(o.payment_time, '%Y-%m')</when>" +
            "    <when test=\"dimension == 'WEEKLY'\">DATE_FORMAT(o.payment_time, '%x-W%v')</when>" +
            "    <otherwise>DATE_FORMAT(o.payment_time, '%Y-%m-%d')</otherwise>" +
            "  </choose> AS period, " +
            "  COUNT(DISTINCT o.id) AS orderCount, " +
            "  SUM(CASE WHEN o.status = 'REFUNDED' THEN 0 ELSE GREATEST(IFNULL(o.pay_amount, 0) - IFNULL(r.refund_amount, 0), 0) END) AS netSalesAmount, " +
            "  SUM(CASE WHEN o.status = 'REFUNDED' THEN 0 ELSE IFNULL(r.valid_cost, 0) END) AS costAmount, " +
            "  SUM(IFNULL(r.valid_qty, 0)) AS goodsCount " +
            "FROM oms_order o " +
            "LEFT JOIN (" +
            "  SELECT order_no, " +
            "    SUM(IFNULL(return_quantity, 0) * IFNULL(goods_price, 0)) AS refund_amount, " +
            "    SUM((IFNULL(quantity, 0) - IFNULL(return_quantity, 0)) * IFNULL(purchase_price, 0)) AS valid_cost, " +
            "    SUM(IFNULL(quantity, 0) - IFNULL(return_quantity, 0)) AS valid_qty " +
            "  FROM oms_order_detail GROUP BY order_no" +
            ") r ON o.order_no = r.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL', 'REFUNDED') " +
            "  AND o.payment_time &gt;= #{startTime} AND o.payment_time &lt;= #{endTime} " +
            "GROUP BY period ORDER BY period ASC" +
            "</script>")
    List<AnalysisAtomicDataDTO> getPeriodAtomicStats(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("dimension") String dimension);


    // --- 3. Top 商品动销排行 (扣除退货件数) ---
    @Select("SELECT " +
            "  d.goods_name AS goodsName, " +
            "  SUM(IFNULL(d.quantity, 0) - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((IFNULL(d.quantity, 0) - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL', 'REFUNDED') " +
            "  AND o.payment_time >= #{startTime} AND o.payment_time <= #{endTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING salesQty > 0 " +
            "ORDER BY salesQty DESC LIMIT 50")
    List<GoodsSalesRankVO> getTopGoodsRank(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);


    // --- 4. 品牌净贡献度 (扣除退货) ---
    @Select("SELECT " +
            "  IFNULL(b.name, '无品牌/未知') AS brandName, " +
            "  SUM((IFNULL(d.quantity, 0) - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "LEFT JOIN gms_brand b ON d.brand_id = b.id " +
            "WHERE o.status IN ('PAID', 'PARTIAL', 'REFUNDED') " +
            "  AND o.payment_time >= #{startTime} AND o.payment_time <= #{endTime} " +
            "GROUP BY d.brand_id, brandName " +
            "HAVING salesAmount > 0 " +
            "ORDER BY salesAmount DESC")
    List<BrandSalesVO> getBrandSalesDistribution(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);


    // --- 5. 营销核销与 ROI 统计 (剔除全额退款) ---
    @Select("SELECT " +
            "  '满减券' AS ruleType, " +
            "  IFNULL(remark, '通用满减活动') AS ruleName, " +
            "  COUNT(id) AS usedCount, " +
            "  SUM(use_voucher_amount) AS totalDiscountGived, " +
            "  SUM(IFNULL(final_sales_amount, pay_amount)) AS totalRevenueBrought " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'PARTIAL') " +
            "  AND payment_time >= #{startTime} AND payment_time <= #{endTime} " +
            "  AND use_voucher_amount > 0 " +
            "GROUP BY ruleName " +
            "UNION ALL " +
            "SELECT " +
            "  '会员资产' AS ruleType, " +
            "  '会员专属券核销' AS ruleName, " +
            "  COUNT(id) AS usedCount, " +
            "  SUM(coupon_amount) AS totalDiscountGived, " +
            "  SUM(IFNULL(final_sales_amount, pay_amount)) AS totalRevenueBrought " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'PARTIAL') " +
            "  AND payment_time >= #{startTime} AND payment_time <= #{endTime} " +
            "  AND coupon_amount > 0")
    List<MarketingRoiVO> getMarketingRoiStats(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}