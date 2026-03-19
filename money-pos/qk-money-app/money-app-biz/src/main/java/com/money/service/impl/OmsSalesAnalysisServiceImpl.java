package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.*;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.entity.OmsOrder;
import com.money.entity.SysStrategy;
import com.money.mapper.*;
import com.money.service.OmsSalesAnalysisService;
import com.money.util.MoneyUtil;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 经营分析大盘 业务实现类 (V2.5 最终整合版)
 * 职责：协调四大分析集市 Mapper，实现财务审计、销售大盘、客流罗盘的精准计算
 */
@Service
@RequiredArgsConstructor
public class OmsSalesAnalysisServiceImpl implements OmsSalesAnalysisService {

    private final OmsOrderMapper omsOrderMapper;
    private final SysStrategyMapper sysStrategyMapper;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;
    private final OmsOrderTrafficMapper omsOrderTrafficMapper;

    // 🌟 核心补全：注入审计风控专职管家
    private final OmsOrderAuditMapper omsOrderAuditMapper;

    // 自适应前端时间格式引擎
    private LocalDateTime parseStartTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().minusDays(29).atStartOfDay();
        if (dateStr.length() == 10) return LocalDate.parse(dateStr).atStartOfDay();
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private LocalDateTime parseEndTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().atTime(LocalTime.MAX);
        if (dateStr.length() == 10) return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public SalesDashboardVO getSalesDashboard(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        SalesDashboardVO vo = new SalesDashboardVO();
        List<AnalysisAtomicDataDTO> dailyStats = omsOrderAnalysisMapper.getPeriodAtomicStats(startTime, endTime, "DAILY");
        Map<String, AnalysisAtomicDataDTO> statsMap = dailyStats.stream()
                .collect(Collectors.toMap(AnalysisAtomicDataDTO::getPeriod, stat -> stat));

        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        int totalOrderCount = 0;
        int totalGoodsCount = 0;
        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendSales = new ArrayList<>();
        List<Integer> trendOrders = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String dbPeriodKey = date.toString();
            trendDates.add(date.format(formatter));
            AnalysisAtomicDataDTO dailyStat = statsMap.get(dbPeriodKey);
            if (dailyStat != null) {
                totalSalesAmount = totalSalesAmount.add(dailyStat.getNetSalesAmount());
                if (dailyStat.getNetSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
                    totalOrderCount += dailyStat.getOrderCount();
                }
                totalGoodsCount += dailyStat.getGoodsCount();
                trendSales.add(dailyStat.getNetSalesAmount());
                trendOrders.add(dailyStat.getOrderCount());
            } else {
                trendSales.add(BigDecimal.ZERO);
                trendOrders.add(0);
            }
        }

        vo.setTotalSalesAmount(totalSalesAmount);
        vo.setTotalOrderCount(totalOrderCount);
        vo.setTotalGoodsCount(totalGoodsCount);
        vo.setAvgOrderValue(totalOrderCount > 0 ? totalSalesAmount.divide(new BigDecimal(totalOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        vo.setTrendDates(trendDates);
        vo.setTrendSales(trendSales);
        vo.setTrendOrders(trendOrders);
        vo.setTopGoodsRanking(omsOrderAnalysisMapper.getTopGoodsRank(startTime, endTime));
        vo.setBrandDistribution(omsOrderAnalysisMapper.getBrandSalesDistribution(startTime, endTime));

        return vo;
    }

    @Override
    public List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);
        List<AnalysisAtomicDataDTO> stats = omsOrderAnalysisMapper.getPeriodAtomicStats(startTime, endTime, dimension);
        List<PerformanceReportVO> result = new ArrayList<>();
        for (AnalysisAtomicDataDTO stat : stats) {
            PerformanceReportVO vo = new PerformanceReportVO(
                    stat.getPeriod(), stat.getOrderCount(), stat.getGoodsCount(),
                    stat.getNetSalesAmount(), stat.getAsp()
            );
            result.add(vo);
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);
        List<MarketingRoiVO> results = omsOrderAnalysisMapper.getMarketingRoiStats(startTime, endTime);
        for (MarketingRoiVO vo : results) {
            BigDecimal revenue = vo.getTotalRevenueBrought() != null ? vo.getTotalRevenueBrought() : BigDecimal.ZERO;
            BigDecimal cost = vo.getTotalDiscountGived() != null ? vo.getTotalDiscountGived() : BigDecimal.ZERO;
            int usedCount = vo.getUsedCount() != null ? vo.getUsedCount() : 0;
            vo.setRoiMultiplier(cost.compareTo(BigDecimal.ZERO) > 0 ? revenue.divide(cost, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            vo.setAvgOrderValue(usedCount > 0 ? revenue.divide(new BigDecimal(usedCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }
        results.sort((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier()));
        return results;
    }

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<AnalysisAtomicDataDTO> stats = omsOrderAnalysisMapper.getPeriodAtomicStats(startTime, endTime, "DAILY");
        OrderCountVO vo = new OrderCountVO();
        long totalOrder = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (AnalysisAtomicDataDTO stat : stats) {
            totalOrder += stat.getOrderCount();
            totalSales = MoneyUtil.add(totalSales, stat.getNetSalesAmount());
            totalCost = MoneyUtil.add(totalCost, stat.getCostAmount());
        }

        vo.setOrderCount(totalOrder);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales);
        vo.setCostCount(totalCost);
        vo.setProfit(MoneyUtil.subtract(totalSales, totalCost));
        return vo;
    }

    // 🌟 财务核算穿透：调用 OmsOrderAuditMapper 查明细快照并处理分页
    @Override
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        Page<ProfitAuditVO> page = omsOrderAuditMapper.getProfitAuditPage(
                PageUtil.toPage(queryDTO),
                queryDTO.getOrderNo(),
                queryDTO.getStatus()
        );
        return PageUtil.toPageVO(page);
    }

    @Override
    public List<HourlyTrafficVO> getTrafficAnalysis(Integer dayOfWeek) {
        Double divisor = (dayOfWeek != null) ? 4.0 : 28.0;
        Integer mysqlDow = null;
        if (dayOfWeek != null) {
            mysqlDow = (dayOfWeek == 7) ? 1 : (dayOfWeek + 1);
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(28);

        List<HourlyTrafficVO> dbData = omsOrderTrafficMapper.getHourlyTrafficAnalysis(startTime, endTime, mysqlDow, divisor);

        Map<Integer, HourlyTrafficVO> dataMap = new HashMap<>();
        if (dbData != null) {
            for (HourlyTrafficVO vo : dbData) {
                dataMap.put(vo.getHour(), vo);
            }
        }

        List<HourlyTrafficVO> full24Hours = new ArrayList<>();

        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        BigDecimal safeOrderThreshold = new BigDecimal("1.0");
        BigDecimal safeValueThreshold = new BigDecimal("50.0");
        if (strategy != null) {
            if (strategy.getTrafficOrderThreshold() != null) safeOrderThreshold = strategy.getTrafficOrderThreshold();
            if (strategy.getTrafficValueThreshold() != null) safeValueThreshold = strategy.getTrafficValueThreshold();
        }

        for (int i = 0; i < 24; i++) {
            HourlyTrafficVO vo = dataMap.get(i);
            if (vo == null) {
                vo = new HourlyTrafficVO();
                vo.setHour(i);
                vo.setAvgOrderCount(BigDecimal.ZERO);
                vo.setAvgSalesAmount(BigDecimal.ZERO);
            }

            boolean isSafeToLeave = vo.getAvgOrderCount().compareTo(safeOrderThreshold) < 0
                    && vo.getAvgSalesAmount().compareTo(safeValueThreshold) < 0;

            vo.setSuggestion(isSafeToLeave ? "OUT" : "STAY");
            full24Hours.add(vo);
        }

        return full24Hours;
    }

    @Override
    public List<TimeTrafficVO> getWeeklyTraffic() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        int days = (strategy != null && strategy.getWeeklyAnalysisDays() != null) ? strategy.getWeeklyAnalysisDays() : 90;
        double divisor = days / 7.0;

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        return omsOrderTrafficMapper.getWeeklyTrafficAnalysis(startTime, endTime, divisor);
    }

    @Override
    public List<TimeTrafficVO> getMonthlyTraffic() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        int days = (strategy != null && strategy.getMonthlyAnalysisDays() != null) ? strategy.getMonthlyAnalysisDays() : 180;
        double divisor = days / 30.43;

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        return omsOrderTrafficMapper.getMonthlyTrafficAnalysis(startTime, endTime, divisor);
    }
}