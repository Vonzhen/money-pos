package com.money.mapper;

import com.money.entity.OmsOrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 订单明细表 Mapper 接口 (大盘净值纯化版)
 * </p>
 */
public interface OmsOrderDetailMapper extends BaseMapper<OmsOrderDetail> {

    // 1. 近7日走势图 (🌟 直接查询主订单表 oms_order，与财务大屏 100% 对齐)
    @org.apache.ibatis.annotations.Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS date, " +
            "SUM(IFNULL(final_sales_amount, pay_amount)) AS sales, " +
            "SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) AS profit " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'DONE') AND create_time >= #{startTime} " +
            "GROUP BY DATE(create_time), DATE_FORMAT(create_time, '%m-%d') " +
            "ORDER BY DATE(create_time) ASC")
    java.util.List<com.money.dto.Home.TrendChartVO> getTrendData(@org.apache.ibatis.annotations.Param("startTime") java.time.LocalDateTime startTime);

    // 2. 品牌营收饼图 (保持不变，因为品牌维度的贡献度通常采用商品原始售价进行比例切分最合理)
    @org.apache.ibatis.annotations.Select("SELECT IFNULL(gb.name, '无品牌/未知') AS name, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS value " +
            "FROM oms_order_detail ood " +
            "LEFT JOIN gms_brand gb ON ood.brand_id = gb.id " +
            "WHERE ood.status = 'PAID' " +
            "AND ood.order_no NOT IN (SELECT order_no FROM oms_order WHERE status = 'RETURN' OR IFNULL(final_sales_amount, pay_amount) <= 0) " +
            "GROUP BY gb.id, gb.name")
    java.util.List<com.money.dto.Home.BrandPieVO> getBrandPieData();

}