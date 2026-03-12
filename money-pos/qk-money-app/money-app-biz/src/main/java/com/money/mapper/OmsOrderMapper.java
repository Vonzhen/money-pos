package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.entity.OmsOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 订单表 Mapper 接口
 * </p>
 */
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    /**
     * 🌟 真实损益毛利审计 底层聚合查询
     * 结合了快照成本(purchase_price)进行强力回溯，支持分母为 0 的防御
     */
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
            "WHERE status IN ('PAID', 'RETURN') " +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no = #{orderNo} </if>" +
            "<if test='anomalyOnly != null and anomalyOnly == true'> AND (purchase_price IS NULL OR purchase_price &lt;= 0 OR (goods_price - purchase_price) &lt; 0) </if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    Page<ProfitAuditVO> getProfitAuditPage(
            Page<?> page,
            @Param("orderNo") String orderNo,
            @Param("anomalyOnly") Boolean anomalyOnly);

}