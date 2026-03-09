package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.OmsOrder.OmsSalesDataVO.*;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.service.GmsBrandService;
import com.money.service.GmsGoodsService;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import com.money.service.OmsSalesAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OmsSalesAnalysisServiceImpl implements OmsSalesAnalysisService {

    private final OmsOrderService omsOrderService;
    private final OmsOrderDetailService omsOrderDetailService;
    private final GmsGoodsService gmsGoodsService;
    private final GmsBrandService gmsBrandService;

    @Override
    public SalesDashboardVO getSalesDashboard(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        SalesDashboardVO vo = new SalesDashboardVO();

        // 1. 捞取期间内所有有效订单
        List<OmsOrder> orders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, startTime).le(OmsOrder::getPaymentTime, endTime)
                .in(OmsOrder::getStatus, "PAID", "RETURN"));

        if (orders.isEmpty()) {
            vo.setTotalSalesAmount(BigDecimal.ZERO);
            vo.setTotalOrderCount(0);
            vo.setTotalGoodsCount(0);
            vo.setAvgOrderValue(BigDecimal.ZERO);
            return vo;
        }

        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        for (OmsOrder o : orders) {
            totalSalesAmount = totalSalesAmount.add(o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO);
        }

        int totalOrderCount = orders.size();
        vo.setTotalSalesAmount(totalSalesAmount);
        vo.setTotalOrderCount(totalOrderCount);
        vo.setAvgOrderValue(totalOrderCount > 0 ? totalSalesAmount.divide(new BigDecimal(totalOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // 2. 捞取订单明细进行 SKU 和品牌维度的降维打击
        List<String> orderNos = orders.stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>()
                .in(OmsOrderDetail::getOrderNo, orderNos));

        int totalGoodsCount = 0;
        Map<String, GoodsSalesRankVO> goodsRankMap = new HashMap<>();
        Map<String, BrandSalesVO> brandSalesMap = new HashMap<>();
        Map<Long, String> brandNameCache = new HashMap<>();

        for (OmsOrderDetail d : details) {
            int qty = d.getQuantity() != null ? d.getQuantity() : 0;
            // 剔除已退货的数量
            int actualQty = qty - (d.getReturnQuantity() != null ? d.getReturnQuantity() : 0);
            if (actualQty <= 0) continue;

            totalGoodsCount += actualQty;
            BigDecimal itemSales = (d.getGoodsPrice() != null ? d.getGoodsPrice() : BigDecimal.ZERO).multiply(new BigDecimal(actualQty));

            // 统计单品排行
            GoodsSalesRankVO rankVO = goodsRankMap.getOrDefault(d.getGoodsName(), new GoodsSalesRankVO(d.getGoodsName(), 0, BigDecimal.ZERO));
            rankVO.setSalesQty(rankVO.getSalesQty() + actualQty);
            rankVO.setSalesAmount(rankVO.getSalesAmount().add(itemSales));
            goodsRankMap.put(d.getGoodsName(), rankVO);

            // 统计品牌分布
            String brandName = "无品牌/散件";
            if (d.getGoodsId() != null) {
                if (brandNameCache.containsKey(d.getGoodsId())) {
                    brandName = brandNameCache.get(d.getGoodsId());
                } else {
                    GmsGoods goods = gmsGoodsService.getById(d.getGoodsId());
                    if (goods != null && goods.getBrandId() != null) {
                        GmsBrand brand = gmsBrandService.getById(goods.getBrandId());
                        if (brand != null && brand.getName() != null) brandName = brand.getName();
                    }
                    brandNameCache.put(d.getGoodsId(), brandName);
                }
            }
            BrandSalesVO brandVO = brandSalesMap.getOrDefault(brandName, new BrandSalesVO(brandName, BigDecimal.ZERO));
            brandVO.setSalesAmount(brandVO.getSalesAmount().add(itemSales));
            brandSalesMap.put(brandName, brandVO);
        }
        vo.setTotalGoodsCount(totalGoodsCount);

        // 排序组装
        List<GoodsSalesRankVO> rankList = new ArrayList<>(goodsRankMap.values());
        rankList.sort((a, b) -> b.getSalesQty().compareTo(a.getSalesQty()));
        vo.setTopGoodsRanking(rankList.size() > 50 ? rankList.subList(0, 50) : rankList);

        List<BrandSalesVO> brandList = new ArrayList<>(brandSalesMap.values());
        brandList.sort((a, b) -> b.getSalesAmount().compareTo(a.getSalesAmount()));
        vo.setBrandDistribution(brandList);

        // 3. 组装每日双轨趋势
        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendSales = new ArrayList<>();
        List<Integer> trendOrders = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            trendDates.add(currentDate.format(formatter));

            BigDecimal dailySales = BigDecimal.ZERO;
            int dailyOrdersCount = 0;

            for (OmsOrder o : orders) {
                if (o.getPaymentTime().toLocalDate().equals(currentDate)) {
                    dailySales = dailySales.add(o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO);
                    dailyOrdersCount++;
                }
            }
            trendSales.add(dailySales);
            trendOrders.add(dailyOrdersCount);
        }

        vo.setTrendDates(trendDates);
        vo.setTrendSales(trendSales);
        vo.setTrendOrders(trendOrders);

        return vo;
    }
    @Override
    public List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 1. 捞取有效订单
        List<OmsOrder> orders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, startTime).le(OmsOrder::getPaymentTime, endTime)
                .in(OmsOrder::getStatus, "PAID", "RETURN"));

        if (orders.isEmpty()) return new ArrayList<>();

        // 2. 捞取明细，用于计算真实的商品出库件数
        List<String> orderNos = orders.stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>()
                .in(OmsOrderDetail::getOrderNo, orderNos));

        // 预处理：计算每个订单的实际动销件数 (购买数 - 退货数)
        Map<String, Integer> orderGoodsCountMap = details.stream().collect(Collectors.groupingBy(
                OmsOrderDetail::getOrderNo,
                Collectors.summingInt(d -> (d.getQuantity() != null ? d.getQuantity() : 0) - (d.getReturnQuantity() != null ? d.getReturnQuantity() : 0))
        ));

        // 3. 核心降维逻辑：按指定的维度 (日/周/月) 进行聚合分组
        // 使用 TreeMap 并倒序排列，保证前端展示时最新的日期在最上面
        Map<String, PerformanceReportVO> reportMap = new TreeMap<>(Collections.reverseOrder());
        DateTimeFormatter dailyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (OmsOrder order : orders) {
            LocalDateTime time = order.getPaymentTime();
            String periodKey = "";

            if ("MONTHLY".equalsIgnoreCase(dimension)) {
                periodKey = time.format(monthlyFormatter);
            } else if ("WEEKLY".equalsIgnoreCase(dimension)) {
                // 按标准的 ISO 周进行聚合 (如 2024-W21)
                int week = time.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                int year = time.get(IsoFields.WEEK_BASED_YEAR);
                periodKey = year + "-W" + String.format("%02d", week);
            } else {
                // 默认按 DAILY
                periodKey = time.format(dailyFormatter);
            }

            PerformanceReportVO vo = reportMap.getOrDefault(periodKey, new PerformanceReportVO(periodKey, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO));

            vo.setOrderCount(vo.getOrderCount() + 1);
            vo.setGoodsCount(vo.getGoodsCount() + orderGoodsCountMap.getOrDefault(order.getOrderNo(), 0));
            vo.setSalesAmount(vo.getSalesAmount().add(order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO));

            reportMap.put(periodKey, vo);
        }

        // 4. 计算各周期的平均客单价
        List<PerformanceReportVO> result = new ArrayList<>(reportMap.values());
        for (PerformanceReportVO vo : result) {
            if (vo.getOrderCount() > 0) {
                vo.setAvgOrderValue(vo.getSalesAmount().divide(new BigDecimal(vo.getOrderCount()), 2, RoundingMode.HALF_UP));
            }
        }

        return result;
    }
    @Override
    public List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();

        // 1. 捞取所有核销过优惠的订单 (包括满减券和会员券)
        List<OmsOrder> orders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, LocalDateTime.of(start, LocalTime.MIN))
                .le(OmsOrder::getPaymentTime, LocalDateTime.of(end, LocalTime.MAX))
                .and(w -> w.gt(OmsOrder::getUseVoucherAmount, 0).or().gt(OmsOrder::getCouponAmount, 0))
                .eq(OmsOrder::getStatus, "PAID"));

        if (orders.isEmpty()) return new ArrayList<>();

        // 2. 内存聚合分析
        Map<String, MarketingRoiVO> reportMap = new HashMap<>();

        for (OmsOrder o : orders) {
            // A. 处理满减券 (Voucher)
            if (o.getUseVoucherAmount() != null && o.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0) {
                // 假设备注里存了券名称，或者关联了 ruleId，这里简化为汇总
                String name = (o.getRemark() != null && o.getRemark().contains("券")) ? o.getRemark() : "通用满减活动";
                updateRoiMap(reportMap, name, "满减券", o.getUseVoucherAmount(), o.getPayAmount());
            }

            // B. 处理会员券核销 (Member Coupon)
            if (o.getCouponAmount() != null && o.getCouponAmount().compareTo(BigDecimal.ZERO) > 0) {
                updateRoiMap(reportMap, "会员专属券核销", "会员资产", o.getCouponAmount(), o.getPayAmount());
            }
        }

        // 3. 计算最终倍数
        List<MarketingRoiVO> results = new ArrayList<>(reportMap.values());
        for (MarketingRoiVO vo : results) {
            if (vo.getTotalDiscountGived().compareTo(BigDecimal.ZERO) > 0) {
                vo.setRoiMultiplier(vo.getTotalRevenueBrought().divide(vo.getTotalDiscountGived(), 2, RoundingMode.HALF_UP));
            }
            if (vo.getUsedCount() > 0) {
                vo.setAvgOrderValue(vo.getTotalRevenueBrought().divide(new BigDecimal(vo.getUsedCount()), 2, RoundingMode.HALF_UP));
            }
        }

        // 按 ROI 倍数降序排列
        results.sort((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier()));
        return results;
    }

    private void updateRoiMap(Map<String, MarketingRoiVO> map, String name, String type, BigDecimal discount, BigDecimal revenue) {
        MarketingRoiVO vo = map.getOrDefault(name, new MarketingRoiVO());
        vo.setRuleName(name);
        vo.setRuleType(type);
        vo.setUsedCount((vo.getUsedCount() == null ? 0 : vo.getUsedCount()) + 1);
        vo.setTotalDiscountGived((vo.getTotalDiscountGived() == null ? BigDecimal.ZERO : vo.getTotalDiscountGived()).add(discount));
        vo.setTotalRevenueBrought((vo.getTotalRevenueBrought() == null ? BigDecimal.ZERO : vo.getTotalRevenueBrought()).add(revenue));
        map.put(name, vo);
    }
}