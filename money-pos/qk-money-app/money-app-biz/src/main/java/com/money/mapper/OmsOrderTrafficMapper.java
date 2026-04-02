package com.money.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 客流罗盘集市 Mapper (OLAP)
 */
@Mapper
public interface OmsOrderTrafficMapper {

    @Select("<script>" +
            "SELECT " +
            "  HOUR(create_time) as hour, " +
            "  COUNT(id) / #{divisor} as avgOrderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) / #{divisor} as avgSalesAmount " +
            "FROM oms_order " +
            "WHERE create_time &gt;= #{startTime} AND create_time &lt;= #{endTime} " +
            // 🌟 洗缩：踢出全额退款单，防止客流潮汐被“假忙碌”高估
            "  AND status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "<if test='dayOfWeek != null'> AND DAYOFWEEK(create_time) = #{dayOfWeek} </if> " +
            "GROUP BY HOUR(create_time) " +
            "ORDER BY hour ASC" +
            "</script>")
    List<com.money.dto.OmsOrder.OmsSalesDataVO.HourlyTrafficVO> getHourlyTrafficAnalysis(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("divisor") Double divisor);

    @Select("SELECT " +
            "  DAYOFWEEK(create_time) as timeKey, " +
            "  COUNT(id) / #{divisor} as avgOrderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) / #{divisor} as avgSalesAmount " +
            "FROM oms_order " +
            "WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            // 🌟 洗缩：踢出全额退款单
            "  AND status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "GROUP BY timeKey ORDER BY timeKey ASC")
    List<com.money.dto.OmsOrder.OmsSalesDataVO.TimeTrafficVO> getWeeklyTrafficAnalysis(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("divisor") Double divisor);

    @Select("SELECT " +
            "  DAY(create_time) as timeKey, " +
            "  COUNT(id) / #{divisor} as avgOrderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) / #{divisor} as avgSalesAmount " +
            "FROM oms_order " +
            "WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            // 🌟 洗缩：踢出全额退款单
            "  AND status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "GROUP BY timeKey ORDER BY timeKey ASC")
    List<com.money.dto.OmsOrder.OmsSalesDataVO.TimeTrafficVO> getMonthlyTrafficAnalysis(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("divisor") Double divisor);
}