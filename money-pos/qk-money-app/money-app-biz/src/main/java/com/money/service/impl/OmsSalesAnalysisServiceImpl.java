package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.*;
import com.money.mapper.OmsOrderMapper;
import com.money.service.OmsSalesAnalysisService;
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

@Service
@RequiredArgsConstructor
public class OmsSalesAnalysisServiceImpl implements OmsSalesAnalysisService {

    private final OmsOrderMapper omsOrderMapper;

    // 🌟 自适应前端时间格式引擎：不管前端传的是 YYYY-MM-DD 还是带时分秒，统一补齐到极值
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
        List<AnalysisAtomicDataDTO> dailyStats = omsOrderMapper.getPeriodAtomicStats(startTime, endTime, "DAILY");
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

        vo.setTopGoodsRanking(omsOrderMapper.getTopGoodsRank(startTime, endTime));
        vo.setBrandDistribution(omsOrderMapper.getBrandSalesDistribution(startTime, endTime));

        return vo;
    }

    @Override
    public List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        List<AnalysisAtomicDataDTO> stats = omsOrderMapper.getPeriodAtomicStats(startTime, endTime, dimension);
        List<PerformanceReportVO> result = new ArrayList<>();
        for (AnalysisAtomicDataDTO stat : stats) {
            PerformanceReportVO vo = new PerformanceReportVO(
                    stat.getPeriod(),
                    stat.getOrderCount(),
                    stat.getGoodsCount(),
                    stat.getNetSalesAmount(),
                    stat.getAsp()
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

        List<MarketingRoiVO> results = omsOrderMapper.getMarketingRoiStats(startTime, endTime);
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
}