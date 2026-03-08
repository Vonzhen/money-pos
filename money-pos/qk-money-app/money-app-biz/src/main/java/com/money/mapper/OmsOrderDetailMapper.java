package com.money.mapper;

import com.money.entity.OmsOrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 订单明细表 Mapper 接口
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface OmsOrderDetailMapper extends BaseMapper<OmsOrderDetail> {
    // 1. 近7日走势图
    @org.apache.ibatis.annotations.Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS date, " +
            "SUM((quantity - IFNULL(return_quantity, 0)) * IFNULL(goods_price, 0)) AS sales, " +
            "SUM((quantity - IFNULL(return_quantity, 0)) * (IFNULL(goods_price, 0) - IFNULL(purchase_price, 0))) AS profit " +
            "FROM oms_order_detail " +
            "WHERE status = 'PAID' AND create_time >= #{startTime} " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d'), DATE_FORMAT(create_time, '%m-%d') " +
            "ORDER BY DATE_FORMAT(create_time, '%Y-%m-%d') ASC")
    java.util.List<com.money.dto.Home.TrendChartVO> getTrendData(@org.apache.ibatis.annotations.Param("startTime") java.time.LocalDateTime startTime);

    // 2. 品牌营收饼图
    @org.apache.ibatis.annotations.Select("SELECT IFNULL(gb.name, '无品牌/未知') AS name, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS value " +
            "FROM oms_order_detail ood " +
            "LEFT JOIN gms_goods gg ON ood.goods_id = gg.id " +
            "LEFT JOIN gms_brand gb ON gg.brand_id = gb.id " +
            "WHERE ood.status = 'PAID' " +
            "GROUP BY gb.id, gb.name")
    java.util.List<com.money.dto.Home.BrandPieVO> getBrandPieData();

}
