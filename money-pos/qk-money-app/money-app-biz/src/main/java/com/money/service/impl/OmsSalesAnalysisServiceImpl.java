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
 * 经营分析大盘 业务实现类 (V3.0 决策引擎升级版)
 */
@Service
@RequiredArgsConstructor
public class OmsSalesAnalysisServiceImpl implements OmsSalesAnalysisService {

    private final OmsOrderMapper omsOrderMapper;
    private final SysStrategyMapper sysStrategyMapper;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;
    private final OmsOrderTrafficMapper omsOrderTrafficMapper;
    private final OmsOrderAuditMapper omsOrderAuditMapper;

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
        // 🌟 P0-1 新增：客单价趋势数组
        List<BigDecimal> trendAsp = new ArrayList<>();

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

                // 🌟 P0-1 核心计算：组装每日 ASP
                if (dailyStat.getOrderCount() > 0) {
                    trendAsp.add(dailyStat.getNetSalesAmount().divide(new BigDecimal(dailyStat.getOrderCount()), 2, RoundingMode.HALF_UP));
                } else {
                    trendAsp.add(BigDecimal.ZERO);
                }
            } else {
                trendSales.add(BigDecimal.ZERO);
                trendOrders.add(0);
                trendAsp.add(BigDecimal.ZERO);
            }
        }

        vo.setTotalSalesAmount(totalSalesAmount);
        vo.setTotalOrderCount(totalOrderCount);
        vo.setTotalGoodsCount(totalGoodsCount);
        vo.setAvgOrderValue(totalOrderCount > 0 ? totalSalesAmount.divide(new BigDecimal(totalOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        vo.setTrendDates(trendDates);
        vo.setTrendSales(trendSales);
        vo.setTrendOrders(trendOrders);
        vo.setTrendAsp(trendAsp); // 注入 ASP 数组

        vo.setTopGoodsRanking(omsOrderAnalysisMapper.getTopGoodsRank(startTime, endTime));
        vo.setBrandDistribution(omsOrderAnalysisMapper.getBrandSalesDistribution(startTime, endTime));

        // ==========================================
        // 🌟 P0-2 核心组装：会员 vs 散客双线趋势
        // ==========================================
        List<DailyMemberStatDTO> memberStats = omsOrderAnalysisMapper.getDailyMemberStats(startTime, endTime);
        MemberTrendVO memberTrendVO = new MemberTrendVO();
        memberTrendVO.setDates(trendDates);

        List<BigDecimal> memberSales = new ArrayList<>();
        List<BigDecimal> guestSales = new ArrayList<>();
        List<BigDecimal> memberAsp = new ArrayList<>();
        List<BigDecimal> guestAsp = new ArrayList<>();

        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String matchDate = date.toString();

            BigDecimal mSales = BigDecimal.ZERO;
            BigDecimal gSales = BigDecimal.ZERO;
            int mOrders = 0;
            int gOrders = 0;

            for (DailyMemberStatDTO stat : memberStats) {
                if (matchDate.equals(stat.getDateStr())) {
                    if (stat.getIsMember() == 1) {
                        mSales = mSales.add(stat.getSalesAmount() != null ? stat.getSalesAmount() : BigDecimal.ZERO);
                        mOrders += (stat.getOrderCount() != null ? stat.getOrderCount() : 0);
                    } else {
                        gSales = gSales.add(stat.getSalesAmount() != null ? stat.getSalesAmount() : BigDecimal.ZERO);
                        gOrders += (stat.getOrderCount() != null ? stat.getOrderCount() : 0);
                    }
                }
            }

            memberSales.add(mSales);
            guestSales.add(gSales);
            memberAsp.add(mOrders > 0 ? mSales.divide(new BigDecimal(mOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            guestAsp.add(gOrders > 0 ? gSales.divide(new BigDecimal(gOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }

        memberTrendVO.setMemberSales(memberSales);
        memberTrendVO.setGuestSales(guestSales);
        memberTrendVO.setMemberAsp(memberAsp);
        memberTrendVO.setGuestAsp(guestAsp);

        vo.setMemberTrend(memberTrendVO); // 注入会员双线数据

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

    @Override
    public List<CategorySalesVO> getCategorySales(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);
        return omsOrderAnalysisMapper.getCategorySalesDistribution(startTime, endTime);
    }

    // ==========================================
    // 🌟 P0-3 核心实现：获取所选 TopN 单品的每日连续销量趋势
    // ==========================================
    @Override
    public List<GoodsTrendVO> getTopGoodsTrend(String startDate, String endDate, List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        // 从数据库一次性捞出这几个商品的每日数据
        List<DailyGoodsStatDTO> rawStats = omsOrderAnalysisMapper.getDailyGoodsStats(startTime, endTime, goodsIds);

        Map<Long, GoodsTrendVO> resultMap = new HashMap<>();
        for (Long id : goodsIds) {
            GoodsTrendVO vo = new GoodsTrendVO();
            vo.setGoodsId(id);
            // 稍后在循环里填充真实的商品名称
            vo.setGoodsName("商品 ID:" + id);
            vo.setTrendSalesQty(new ArrayList<>());
            resultMap.put(id, vo);
        }

        // 把数据库返回的结果按日期和商品铺平
        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String matchDate = date.toString();

            for (Long goodsId : goodsIds) {
                GoodsTrendVO vo = resultMap.get(goodsId);
                int dailyQty = 0;

                for (DailyGoodsStatDTO stat : rawStats) {
                    if (stat.getGoodsId().equals(goodsId)) {
                        vo.setGoodsName(stat.getGoodsName()); // 更新为真实商品名
                        if (matchDate.equals(stat.getDateStr())) {
                            dailyQty += (stat.getSalesQty() != null ? stat.getSalesQty() : 0);
                        }
                    }
                }
                vo.getTrendSalesQty().add(dailyQty);
            }
        }

        return new ArrayList<>(resultMap.values());
    }
}