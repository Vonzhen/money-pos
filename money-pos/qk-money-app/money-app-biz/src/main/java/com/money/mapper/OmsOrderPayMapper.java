package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.entity.OmsOrderPay;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OmsOrderPayMapper extends BaseMapper<OmsOrderPay> {

    /**
     * 🌟 高性能聚合引擎：按天、按渠道、按标签汇总支付流水
     * 🌟 V4.0 口径统一：同时输出毛额(totalAmount)和净额(netAmount)
     */
    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS dateStr, " +
            "p.pay_method_code AS methodCode, " +
            "p.pay_tag AS payTag, " +
            // 原始支付毛额 (无论是否退款，当初收了多少就是多少)
            "SUM(p.pay_amount) AS totalAmount, " +
            // 🌟 修复问题4：引入财务净收入口径 (如果该单已被全额退款，则资金不计入净额)
            "SUM(CASE WHEN o.status = 'REFUNDED' THEN 0 ELSE p.pay_amount END) AS netAmount " +
            "FROM oms_order_pay p " +
            "JOIN oms_order o ON p.order_no = o.order_no " +
            "WHERE o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "GROUP BY DATE(o.create_time), p.pay_method_code, p.pay_tag")
    List<Map<String, Object>> getDailyPaySummary(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 🌟 交接班专属：高精度资金流扫荡
     */
    @Select("<script>" +
            "SELECT p.pay_method_code AS methodCode, " +
            "SUM(p.pay_amount) AS totalAmount, " +
            // 🌟 同步修复交接班时的资金净额
            "SUM(CASE WHEN o.status = 'REFUNDED' THEN 0 ELSE p.pay_amount END) AS netAmount " +
            "FROM oms_order_pay p " +
            "JOIN oms_order o ON p.order_no = o.order_no " +
            "WHERE o.create_time &gt;= #{startTime} AND o.create_time &lt;= #{endTime} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "<if test='cashierName != null and cashierName != \"全部收银员\"'> " +
            "  AND o.create_by = #{cashierName} " +
            "</if>" +
            "GROUP BY p.pay_method_code" +
            "</script>")
    List<Map<String, Object>> getShiftPayStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("cashierName") String cashierName);
}