package com.money.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.*;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Finance", description = "财务大屏及高级报表接口")
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final OmsOrderService omsOrderService;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberService umsMemberService;

    // 【新增注入】为了三大新功能，引入明细和满减券通道
    private final OmsOrderDetailService omsOrderDetailService;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    // ==========================================
    // 1. 原有的：首页今日财务大屏接口
    // ==========================================
    @Operation(summary = "获取今日核心财务数据及图表")
    @GetMapping("/dashboard")
    public FinanceDashboardVO getDashboardData() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(today, LocalTime.MAX);
        LocalDateTime startOf7DaysAgo = LocalDateTime.of(today.minusDays(6), LocalTime.MIN);

        List<OmsOrder> todayOrders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, startOfDay).le(OmsOrder::getPaymentTime, endOfDay).eq(OmsOrder::getStatus, "PAID"));

        List<OmsOrderPay> last7DaysPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOf7DaysAgo).le(OmsOrderPay::getCreateTime, endOfDay).ne(OmsOrderPay::getPayMethodCode, "BALANCE"));

        List<OmsOrderPay> todayPays = last7DaysPays.stream()
                .filter(p -> p.getCreateTime().isAfter(startOfDay) || p.getCreateTime().isEqual(startOfDay)).collect(Collectors.toList());

        FinanceDashboardVO vo = new FinanceDashboardVO();

        vo.setTodayIncome(todayPays.stream().map(OmsOrderPay::getPayAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setTodayProfit(todayOrders.stream().map(o -> (o.getFinalSalesAmount() != null ? o.getFinalSalesAmount() : BigDecimal.ZERO)
                .subtract(o.getCostAmount() != null ? o.getCostAmount() : BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setTodayDiscount(todayOrders.stream().map(o -> (o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .subtract(o.getFinalSalesAmount() != null ? o.getFinalSalesAmount() : BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setTotalDebt(umsMemberService.list().stream().map(UmsMember::getBalance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        Map<String, BigDecimal> payMethodMap = todayPays.stream().collect(Collectors.groupingBy(
                OmsOrderPay::getPayMethodName, Collectors.mapping(OmsOrderPay::getPayAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        List<PayPieData> pieDataList = new ArrayList<>();
        payMethodMap.forEach((name, amount) -> pieDataList.add(new PayPieData(name, amount)));
        vo.setPayBreakdown(pieDataList);

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendIncomes = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        Map<LocalDate, BigDecimal> dailyIncomeMap = last7DaysPays.stream().collect(Collectors.groupingBy(
                p -> p.getCreateTime().toLocalDate(), Collectors.mapping(OmsOrderPay::getPayAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trendDates.add(date.format(formatter));
            trendIncomes.add(dailyIncomeMap.getOrDefault(date, BigDecimal.ZERO));
        }
        vo.setTrendDates(trendDates);
        vo.setTrendIncomes(trendIncomes);

        return vo;
    }

    // ==========================================
    // 2. 【全新】🥇 利润暴击榜（商品毛利排行）
    // ==========================================
    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    public List<ProfitRankVO> getProfitRanking() {
        LocalDateTime startOf30DaysAgo = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);

        // 拿到近30天成功支付的主订单号
        List<String> paidOrderNos = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                        .ge(OmsOrder::getPaymentTime, startOf30DaysAgo).eq(OmsOrder::getStatus, "PAID"))
                .stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());

        if (paidOrderNos.isEmpty()) return new ArrayList<>();

        // 拿到这些订单的所有商品明细
        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>()
                .in(OmsOrderDetail::getOrderNo, paidOrderNos));

        // 按商品名称分组，聚合计算毛利润
        Map<String, ProfitRankVO> rankMap = new HashMap<>();
        for (OmsOrderDetail d : details) {
            BigDecimal qty = new BigDecimal(d.getQuantity());
            // 单件毛利 = 实际售价 - 进货价
            BigDecimal unitProfit = d.getGoodsPrice().subtract(d.getPurchasePrice() != null ? d.getPurchasePrice() : BigDecimal.ZERO);
            BigDecimal totalProfit = unitProfit.multiply(qty);
            BigDecimal totalSales = d.getGoodsPrice().multiply(qty);

            ProfitRankVO vo = rankMap.getOrDefault(d.getGoodsName(), new ProfitRankVO(d.getGoodsName(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
            vo.setTotalQuantity(vo.getTotalQuantity() + d.getQuantity());
            vo.setTotalSales(vo.getTotalSales().add(totalSales));
            vo.setTotalProfit(vo.getTotalProfit().add(totalProfit));
            rankMap.put(d.getGoodsName(), vo);
        }

        // 按利润从高到低排序，取前 50 名
        return rankMap.values().stream()
                .sorted((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()))
                .limit(50).collect(Collectors.toList());
    }

    // ==========================================
    // 3. 【全新】💰 收银交接班对账单
    // ==========================================
    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    public ShiftHandoverVO getShiftHandover(@RequestParam String startTime) {
        // 收银员点击交班时，传入她今天上班的时间 (格式 yyyy-MM-dd HH:mm:ss)
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime shiftStart = LocalDateTime.parse(startTime, dtf);
        LocalDateTime now = LocalDateTime.now();

        // 抓取这段时间的流水
        List<OmsOrderPay> shiftPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, shiftStart).le(OmsOrderPay::getCreateTime, now));

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));

        // 按支付方式分组求和
        Map<String, BigDecimal> breakdown = shiftPays.stream().collect(Collectors.groupingBy(
                OmsOrderPay::getPayMethodName, Collectors.mapping(OmsOrderPay::getPayAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        List<PayPieData> list = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : breakdown.entrySet()) {
            list.add(new PayPieData(entry.getKey(), entry.getValue()));
            // 如果不是本金扣除，就计入抽屉/手机的应有实收总额
            if (!entry.getKey().contains("余额") && !entry.getKey().contains("本金")) {
                totalIncome = totalIncome.add(entry.getValue());
            }
        }
        vo.setPayBreakdown(list);
        vo.setExpectedTotalIncome(totalIncome);

        return vo;
    }

    // ==========================================
    // 4. 【全新】📉 满减活动“大放血”复盘
    // ==========================================
    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    public List<CampaignReviewVO> getCampaignReview() {
        // 找到所有被核销过的满减券记录
        List<PosMemberCoupon> usedCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getStatus, "USED"));

        if (usedCoupons.isEmpty()) return new ArrayList<>();

        // 提取这些满减券关联的订单号
        List<String> orderNos = usedCoupons.stream().map(PosMemberCoupon::getOrderNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<OmsOrder> campaignOrders = orderNos.isEmpty() ? new ArrayList<>() : omsOrderService.list(new LambdaQueryWrapper<OmsOrder>().in(OmsOrder::getOrderNo, orderNos));
        Map<String, OmsOrder> orderMap = campaignOrders.stream().collect(Collectors.toMap(OmsOrder::getOrderNo, o -> o));

        // 提取所有的满减规则
        List<PosCouponRule> allRules = posCouponRuleMapper.selectList(null);
        Map<Long, PosCouponRule> ruleMap = allRules.stream().collect(Collectors.toMap(PosCouponRule::getId, r -> r));

        // 按满减规则ID分组统计
        Map<Long, CampaignReviewVO> reviewMap = new HashMap<>();

        for (PosMemberCoupon uc : usedCoupons) {
            Long ruleId = uc.getRuleId();
            PosCouponRule rule = ruleMap.get(ruleId);
            if (rule == null) continue;

            CampaignReviewVO vo = reviewMap.getOrDefault(ruleId, new CampaignReviewVO(rule.getName(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

            // 累计核销张数和让利金额
            vo.setUsedCount(vo.getUsedCount() + 1);
            vo.setTotalDiscountGived(vo.getTotalDiscountGived().add(rule.getDiscountAmount()));

            // 如果这笔订单还没被统计过营业额，就加进去 (防止一单用多张券导致营业额重复计算)
            OmsOrder order = orderMap.get(uc.getOrderNo());
            if (order != null && !vo.getTrackedOrderNos().contains(order.getOrderNo())) {
                vo.setTotalRevenueBrought(vo.getTotalRevenueBrought().add(order.getFinalSalesAmount() != null ? order.getFinalSalesAmount() : BigDecimal.ZERO));
                vo.getTrackedOrderNos().add(order.getOrderNo());
            }
            reviewMap.put(ruleId, vo);
        }

        // 计算 ROI (投入产出比) 并排序
        List<CampaignReviewVO> result = new ArrayList<>(reviewMap.values());
        for (CampaignReviewVO vo : result) {
            if (vo.getTotalDiscountGived().compareTo(BigDecimal.ZERO) > 0) {
                // 撬动杠杆 = 带来的营业额 / 让利金额 (比如让利20元，带来100元营业额，杠杆就是 5倍)
                vo.setRoiMultiplier(vo.getTotalRevenueBrought().divide(vo.getTotalDiscountGived(), 2, RoundingMode.HALF_UP));
            }
        }
        return result.stream().sorted((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier())).collect(Collectors.toList());
    }

    // ==========================================
    // 数据快递盒 (DTO/VO) 区域
    // ==========================================
    @Data
    public static class FinanceDashboardVO {
        private BigDecimal todayIncome;
        private BigDecimal todayProfit;
        private BigDecimal todayDiscount;
        private BigDecimal totalDebt;
        private List<PayPieData> payBreakdown;
        private List<String> trendDates;
        private List<BigDecimal> trendIncomes;
    }

    @Data
    public static class PayPieData {
        private String name;
        private BigDecimal value;
        public PayPieData(String name, BigDecimal value) { this.name = name; this.value = value; }
    }

    @Data
    public static class ProfitRankVO {
        private String goodsName;
        private Integer totalQuantity;
        private BigDecimal totalSales;
        private BigDecimal totalProfit;
        public ProfitRankVO(String goodsName, Integer totalQuantity, BigDecimal totalSales, BigDecimal totalProfit) {
            this.goodsName = goodsName; this.totalQuantity = totalQuantity; this.totalSales = totalSales; this.totalProfit = totalProfit;
        }
    }

    @Data
    public static class ShiftHandoverVO {
        private String shiftStartTime;
        private String shiftEndTime;
        private BigDecimal expectedTotalIncome;
        private List<PayPieData> payBreakdown;
    }

    @Data
    public static class CampaignReviewVO {
        private String ruleName;
        private Integer usedCount;
        private BigDecimal totalDiscountGived; // 放血多少
        private BigDecimal totalRevenueBrought; // 带来多少营业额
        private BigDecimal roiMultiplier; // 撬动杠杆倍数
        private Set<String> trackedOrderNos = new HashSet<>(); // 内部防重复记账用

        public CampaignReviewVO(String ruleName, Integer usedCount, BigDecimal totalDiscountGived, BigDecimal totalRevenueBrought, BigDecimal roiMultiplier) {
            this.ruleName = ruleName; this.usedCount = usedCount; this.totalDiscountGived = totalDiscountGived; this.totalRevenueBrought = totalRevenueBrought; this.roiMultiplier = roiMultiplier;
        }
    }
}