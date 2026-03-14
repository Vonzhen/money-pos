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
 * 订单明细表 Mapper 接口 (大一统原子操作版)
 * </p>
 */
public interface OmsOrderDetailMapper extends BaseMapper<OmsOrderDetail> {

    /**
     * 🌟 原子退货指令 (针对明细表)
     * 解决：并发超卖、状态撑爆、逻辑外溢
     */
    @Update("UPDATE oms_order_detail " +
            "SET return_quantity = IFNULL(return_quantity, 0) + #{returnQty}, " +
            "    status = CASE " +
            "               WHEN quantity <= IFNULL(return_quantity, 0) + #{returnQty} THEN 'REFUNDED' " +
            "               ELSE status " +
            "             END " +
            "WHERE id = #{detailId} " +
            "  AND (quantity - IFNULL(return_quantity, 0) >= #{returnQty})")
    int refundGoodsAtomically(@Param("detailId") Long detailId, @Param("returnQty") int returnQty);

    // 1. 近7日走势图
    @Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS date, " +
            "SUM(IFNULL(final_sales_amount, pay_amount)) AS sales, " +
            "SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) AS profit " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'DONE', 'REFUNDED') AND create_time >= #{startTime} " +
            "GROUP BY DATE(create_time), DATE_FORMAT(create_time, '%m-%d') " +
            "ORDER BY DATE(create_time) ASC")
    List<com.money.dto.Home.TrendChartVO> getTrendData(@Param("startTime") LocalDateTime startTime);

    // 2. 品牌营收饼图
    @Select("SELECT IFNULL(gb.name, '无品牌/未知') AS name, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS value " +
            "FROM oms_order_detail ood " +
            "LEFT JOIN gms_brand gb ON ood.brand_id = gb.id " +
            "WHERE (ood.status = 'PAID' OR ood.status = 'REFUNDED') " +
            "AND ood.order_no NOT IN (SELECT order_no FROM oms_order WHERE status = 'RETURN' OR IFNULL(final_sales_amount, pay_amount) <= 0) " +
            "GROUP BY gb.id, gb.name")
    List<com.money.dto.Home.BrandPieVO> getBrandPieData();
}