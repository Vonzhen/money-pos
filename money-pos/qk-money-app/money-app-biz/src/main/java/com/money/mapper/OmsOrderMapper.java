package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.entity.OmsOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🌟 交易核心 Mapper (OLTP)
 * 职责：仅处理订单主表读写及退款状态机，保障极速响应
 */
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    @Update("UPDATE oms_order SET " +
            "final_sales_amount = IFNULL(final_sales_amount, 0) - #{refundSales}, " +
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

    List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTrafficAnalysis(int days, double divisor);

    List<Map<String, Object>> getCashierRiskSummary(LocalDateTime startTime, LocalDateTime endTime);
}