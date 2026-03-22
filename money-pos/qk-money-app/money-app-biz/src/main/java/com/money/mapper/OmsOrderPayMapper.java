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
     * 🌟 V3.0 口径统一：强绑定主订单的 create_time 和 经营有效状态集
     */
    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS dateStr, " +
            "p.pay_method_code AS methodCode, " +
            "p.pay_tag AS payTag, " +
            "SUM(p.pay_amount) AS totalAmount " +
            "FROM oms_order_pay p " +
            "JOIN oms_order o ON p.order_no = o.order_no " + // 🌟 强关联：借用主单的时间和状态
            "WHERE o.create_time >= #{startTime} AND o.create_time <= #{endTime} " + // 🌟 锁定主单时间轴
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " + // 🌟 锁定主单有效状态
            "GROUP BY DATE(o.create_time), p.pay_method_code, p.pay_tag")
    List<Map<String, Object>> getDailyPaySummary(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    /**
     * 🌟 交接班专属：高精度资金流扫荡
     * 精确到秒，严格过滤操作人
     */
    @Select("<script>" +
            "SELECT p.pay_method_code AS methodCode, " +
            "SUM(p.pay_amount) AS totalAmount " +
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