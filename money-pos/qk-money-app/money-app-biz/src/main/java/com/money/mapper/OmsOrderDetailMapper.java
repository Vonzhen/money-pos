package com.money.mapper;

import com.money.entity.OmsOrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单明细表 Mapper 接口 (大一统原子操作版 - V2.0 纯净版)
 * </p>
 */
public interface OmsOrderDetailMapper extends BaseMapper<OmsOrderDetail> {

    @Update("UPDATE oms_order_detail " +
            "SET return_quantity = IFNULL(return_quantity, 0) + #{returnQty}, " +
            "    status = CASE " +
            "               WHEN quantity <= IFNULL(return_quantity, 0) + #{returnQty} THEN 'REFUNDED' " +
            "               ELSE status " +
            "             END " +
            "WHERE id = #{detailId} " +
            "  AND (quantity - IFNULL(return_quantity, 0) >= #{returnQty})")
    int refundGoodsAtomically(@Param("detailId") Long detailId, @Param("returnQty") int returnQty);

    // 1. 近7日走势图 (🌟 口径升级：使用最新有效状态集)
    @Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS date, " +
            "SUM(IFNULL(final_sales_amount, pay_amount)) AS sales, " +
            "SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) AS profit " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') AND create_time >= #{startTime} " +
            "GROUP BY DATE(create_time), DATE_FORMAT(create_time, '%m-%d') " +
            "ORDER BY DATE(create_time) ASC")
    List<com.money.dto.Home.TrendChartVO> getTrendData(@Param("startTime") LocalDateTime startTime);

    // 2. 品牌营收饼图 (🌟 口径升级：废弃冗余子查询，直接用退货数量做减法算净收)
    @Select("SELECT IFNULL(gb.name, '无品牌/未知') AS name, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS value " +
            "FROM oms_order_detail ood " +
            "LEFT JOIN gms_brand gb ON ood.brand_id = gb.id " +
            "WHERE ood.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "GROUP BY gb.id, gb.name " +
            "HAVING value > 0")
    List<com.money.dto.Home.BrandPieVO> getBrandPieData();

    // 🌟 3. 高性能利润排行榜 (🌟 口径升级：必须扣除退货数量，展示真实净销和净利)
    @Select("SELECT d.goods_name AS goodsName, " +
            "SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS totalQuantity, " +
            "SUM(d.goods_price * (d.quantity - IFNULL(d.return_quantity, 0))) AS totalSales, " +
            "SUM((d.goods_price - IFNULL(d.purchase_price, 0)) * (d.quantity - IFNULL(d.return_quantity, 0))) AS totalProfit " +
            "FROM oms_order_detail d " +
            "JOIN oms_order o ON o.order_no = d.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "AND o.create_time >= #{startTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING totalQuantity > 0 " +
            "ORDER BY totalProfit DESC " +
            "LIMIT 50")
    List<com.money.dto.Finance.FinanceDataVO.ProfitRankVO> getProfitRankingData(@Param("startTime") LocalDateTime startTime);
}