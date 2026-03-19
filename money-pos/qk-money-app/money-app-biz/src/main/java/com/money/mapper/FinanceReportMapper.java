package com.money.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore; // 🌟 救命稻草：引入 MyBatis-Plus 拦截器忽略注解
import com.money.constant.FinancialMetric;
import com.money.dto.Finance.FinanceDataVO;
import com.money.dto.Finance.FinanceWaterfallVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FinanceReportMapper {

    /**
     * 🌟 全口径资金流统计 (V6.0 突击版)
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
            "      " + FinancialMetric.REFUND_CASH_FORMULA + " AS refundAmount, " +
            "      " + FinancialMetric.NET_SALES_FORMULA + " AS netIncome, " +
            "      0 AS procurementAmount " +
            "    FROM oms_order " +
            "    WHERE status IN (" + FinancialMetric.VALID_STATUS_SQL + ") " +
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

    // ==========================================
    // 🌟 8.1 核心新增：获取首页资产驾驶舱数据 (终极修复版)
    // ==========================================

    /** * 查询今日资产总览
     * 1. 使用 final_sales_amount：完美继承原有逻辑，退款自动扣除！
     * 2. 抓取双轨制字段：waived_coupon_amount (免收) 和 actual_coupon_deduct (核销)
     */
    @Select("SELECT " +
            "  IFNULL(SUM(final_sales_amount), 0) as todayRealCash, " +
            "  IFNULL(SUM(waived_coupon_amount), 0) as todayWaivedAmount, " +
            "  IFNULL(SUM(actual_coupon_deduct), 0) as todayAssetDeduct " +
            "FROM oms_order " +
            "WHERE DATE(create_time) = CURDATE() " +
            "AND status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED')")
    FinanceDataVO.AssetDashboardVO getTodayAssetSummary();

    /** 查询全店会员资产存量占比 */
    @InterceptorIgnore(tenantLine = "true") // 🌟 修复：告诉底层拦截器，这条 SQL 别给我加 tenant_id！
    // ==========================================
    // 🌟 核心点亮：直接穿透会员表，计算全店“真假资产”存量占比
    // ==========================================
    @Select("SELECT " +
            "  IFNULL(SUM(balance), 0) as totalPrincipal, " +
            "  IFNULL(SUM(coupon), 0) as totalGift " +
            "FROM ums_member " +
            "WHERE deleted = 0")
    java.util.Map<String, Object> getAssetComposition();
}