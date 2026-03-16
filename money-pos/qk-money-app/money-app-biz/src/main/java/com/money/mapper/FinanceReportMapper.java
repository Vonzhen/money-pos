package com.money.mapper;

import com.money.constant.FinancialMetric; // 🌟 引入真理模具
import com.money.dto.Finance.FinanceWaterfallVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FinanceReportMapper {

    /**
     * 🌟 全口径资金流统计 (V6.0 突击版)
     * 目标：通过引用 FinancialMetric，确保报表与首页、大屏数据 100% 绝对一致
     */
    @Select("<script>" +
            "SELECT " +
            "  date, " +
            "  SUM(totalAmount) AS totalAmount, " +
            "  SUM(couponAmount) AS couponAmount, " +
            "  SUM(voucherAmount) AS voucherAmount, " +
            "  SUM(manualDiscountAmount) AS manualDiscountAmount, " +
            "  SUM(payAmount) AS payAmount, " +
            "  SUM(refundAmount) AS refundAmount, " +
            "  SUM(netIncome) AS netIncome, " +
            "  SUM(procurementAmount) AS procurementAmount " +
            "FROM (" +
            "    -- 1. 销售流入 (严格套用真理公式) " +
            "    SELECT " +
            "      DATE(create_time) AS date, " +
            "      IFNULL(total_amount, 0) AS totalAmount, " +
            "      IFNULL(coupon_amount, 0) AS couponAmount, " +
            "      IFNULL(use_voucher_amount, 0) AS voucherAmount, " +
            "      IFNULL(manual_discount_amount, 0) AS manualDiscountAmount, " +
            "      IFNULL(pay_amount, 0) AS payAmount, " +
            "      " + FinancialMetric.REFUND_CASH_FORMULA + " AS refundAmount, " + // 🌟 引用退款真理
            "      " + FinancialMetric.NET_SALES_FORMULA + " AS netIncome, " +     // 🌟 引用净收真理
            "      0 AS procurementAmount " +
            "    FROM oms_order " +
            "    WHERE status IN (" + FinancialMetric.VALID_STATUS_SQL + ") " +    // 🌟 引用状态红线
            "    <if test='startTime != null'> AND create_time &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'> AND create_time &lt;= #{endTime} </if>" +
            "    " +
            "    UNION ALL " +
            "    " +
            "    -- 2. 采购流出 " +
            "    SELECT " +
            "      DATE(create_time) AS date, " +
            "      0, 0, 0, 0, 0, 0, 0, " +
            "      IFNULL(total_amount, 0) AS procurementAmount " +
            "    FROM gms_inventory_doc " +
            "    WHERE doc_type = 'INBOUND' " +
            "    <if test='startTime != null'> AND create_time &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'> AND create_time &lt;= #{endTime} </if>" +
            ") t " +
            "GROUP BY date " +
            "ORDER BY date DESC " +
            "</script>")
    List<FinanceWaterfallVO> getDailyWaterfallReport(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}