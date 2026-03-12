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
     * 核心聚合 SQL：
     * 🌟 架构优化：保留严格的 create_time 边界过滤，确保 MySQL 走 B+ 树索引，
     * 过滤出极小的数据集后，再执行聚合操作，防止全表扫描。
     */
    @Select("<script>" +
            "SELECT " +
            "  DATE(create_time) AS date, " +
            "  SUM(IFNULL(total_amount, 0)) AS totalAmount, " +
            "  SUM(IFNULL(coupon_amount, 0)) AS couponAmount, " +
            "  SUM(IFNULL(use_voucher_amount, 0)) AS voucherAmount, " +
            "  SUM(IFNULL(total_amount, 0) - IFNULL(pay_amount, 0) - IFNULL(coupon_amount, 0) - IFNULL(use_voucher_amount, 0)) AS manualDiscountAmount, " +
            "  SUM(IFNULL(pay_amount, 0)) AS payAmount, " +
            "  SUM(IFNULL(pay_amount, 0) - IFNULL(final_sales_amount, 0)) AS refundAmount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) AS netIncome " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'RETURN', 'REFUNDED') " +
            "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'> AND create_time &lt;= #{endTime} </if>" +
            "GROUP BY DATE(create_time) " +
            "ORDER BY date DESC " +
            "</script>")
    List<FinanceWaterfallVO> getDailyWaterfallReport(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}