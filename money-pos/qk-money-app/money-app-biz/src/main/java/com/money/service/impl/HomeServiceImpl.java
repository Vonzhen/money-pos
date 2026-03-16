package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.dto.Home.HomeCountVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.entity.OmsOrder;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.service.GmsGoodsService;
import com.money.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final GmsGoodsService gmsGoodsService;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

    @Override
    public HomeCountVO homeCount() {
        HomeCountVO homeCountVO = new HomeCountVO();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        homeCountVO.setToday(this.executeAggregateQuery(todayStart, tomorrowStart));

        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);
        homeCountVO.setMonth(this.executeAggregateQuery(monthStart, nextMonthStart));

        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        LocalDateTime nextYearStart = yearStart.plusYears(1);
        homeCountVO.setYear(this.executeAggregateQuery(yearStart, nextYearStart));

        homeCountVO.setTotal(this.executeAggregateQuery(null, null));
        homeCountVO.setInventoryValue(gmsGoodsService.getCurrentStockValue());

        return homeCountVO;
    }

    private OrderCountVO executeAggregateQuery(LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<OmsOrder> wrapper = new QueryWrapper<>();

        wrapper.select(
                "COUNT(id) AS orderCount",
                "IFNULL(SUM(IFNULL(final_sales_amount, pay_amount)), 0) AS saleCount",
                "IFNULL(SUM(cost_amount), 0) AS costCount"
        );

        // 🌟 状态归一化：直接调用枚举中的经营有效集，彻底消灭 "PAID", "DONE" 的硬编码
        wrapper.in("status", OrderStatusEnum.getValidFinancialStatus());

        // 🌟 时间口径统一：严格锁定 create_time
        if (startTime != null) {
            wrapper.ge("create_time", startTime);
        }
        if (endTime != null) {
            wrapper.lt("create_time", endTime);
        }

        OrderCountVO vo = new OrderCountVO();
        vo.setOrderCount(0L);
        vo.setSaleCount(BigDecimal.ZERO);
        vo.setCostCount(BigDecimal.ZERO);
        vo.setProfit(BigDecimal.ZERO);

        List<Map<String, Object>> maps = omsOrderMapper.selectMaps(wrapper);
        if (maps != null && !maps.isEmpty() && maps.get(0) != null) {
            Map<String, Object> map = maps.get(0);

            long count = map.get("orderCount") != null ? Long.parseLong(map.get("orderCount").toString()) : 0L;
            BigDecimal sales = map.get("saleCount") != null ? new BigDecimal(map.get("saleCount").toString()) : BigDecimal.ZERO;
            BigDecimal costs = map.get("costCount") != null ? new BigDecimal(map.get("costCount").toString()) : BigDecimal.ZERO;

            vo.setOrderCount(count);
            vo.setSaleCount(sales);
            vo.setCostCount(costs);
            vo.setProfit(sales.subtract(costs));
        }
        return vo;
    }

    @Override
    public com.money.dto.Home.HomeChartsVO getChartsData() {
        com.money.dto.Home.HomeChartsVO chartsVO = new com.money.dto.Home.HomeChartsVO();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);

        chartsVO.setTrendData(omsOrderDetailMapper.getTrendData(sevenDaysAgo));
        chartsVO.setPieData(omsOrderDetailMapper.getBrandPieData());
        chartsVO.setBarData(umsMemberBrandLevelMapper.getMemberBarData());

        return chartsVO;
    }
}