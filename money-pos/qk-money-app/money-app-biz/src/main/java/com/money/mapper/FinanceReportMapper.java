package com.money.mapper;

import com.money.dto.Finance.FinanceWaterfallVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FinanceReportMapper {

    /**
     * 核心聚合 SQL：全口径资金流统计
     * 🌟 架构优化：通过 UNION ALL 将销售流入(oms_order)和采购流出(gms_inventory_doc)在底层数据库直接对冲聚合
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
            "  SUM(procurementAmount) AS procurementAmount " + // 🌟 新增的采购开支
            "FROM (" +
            "    -- 1. 销售部分的资金流入 " +
            "    SELECT " +
            "      DATE(create_time) AS date, " +
            "      IFNULL(total_amount, 0) AS totalAmount, " +
            "      IFNULL(coupon_amount, 0) AS couponAmount, " +
            "      IFNULL(use_voucher_amount, 0) AS voucherAmount, " +
            "      (IFNULL(total_amount, 0) - IFNULL(pay_amount, 0) - IFNULL(coupon_amount, 0) - IFNULL(use_voucher_amount, 0)) AS manualDiscountAmount, " +
            "      IFNULL(pay_amount, 0) AS payAmount, " +
            "      (IFNULL(pay_amount, 0) - IFNULL(final_sales_amount, 0)) AS refundAmount, " +
            "      IFNULL(final_sales_amount, 0) AS netIncome, " +
            "      0 AS procurementAmount " +
            "    FROM oms_order " +
            "    WHERE status IN ('PAID', 'RETURN', 'REFUNDED') " +
            "    <if test='startTime != null'> AND create_time &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'> AND create_time &lt;= #{endTime} </if>" +
            "    " +
            "    UNION ALL " +
            "    " +
            "    -- 2. 采购入库的资金流出 " +
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