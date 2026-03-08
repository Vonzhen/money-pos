package com.money.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.*;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.service.GmsGoodsService;
import com.money.service.GmsBrandService;
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
    private final OmsOrderDetailService omsOrderDetailService;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final GmsGoodsService gmsGoodsService;
    private final GmsBrandService gmsBrandService;

    // ... [保留 dashboard, profit-ranking 接口不变] ...
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

    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    public List<ProfitRankVO> getProfitRanking() {
        LocalDateTime startOf30DaysAgo = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);
        List<String> paidOrderNos = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                        .ge(OmsOrder::getPaymentTime, startOf30DaysAgo).eq(OmsOrder::getStatus, "PAID"))
                .stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());

        if (paidOrderNos.isEmpty()) return new ArrayList<>();

        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>()
                .in(OmsOrderDetail::getOrderNo, paidOrderNos));

        Map<String, ProfitRankVO> rankMap = new HashMap<>();
        for (OmsOrderDetail d : details) {
            BigDecimal qty = new BigDecimal(d.getQuantity());
            BigDecimal unitProfit = d.getGoodsPrice().subtract(d.getPurchasePrice() != null ? d.getPurchasePrice() : BigDecimal.ZERO);
            BigDecimal totalProfit = unitProfit.multiply(qty);
            BigDecimal totalSales = d.getGoodsPrice().multiply(qty);

            ProfitRankVO vo = rankMap.getOrDefault(d.getGoodsName(), new ProfitRankVO(d.getGoodsName(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
            vo.setTotalQuantity(vo.getTotalQuantity() + d.getQuantity());
            vo.setTotalSales(vo.getTotalSales().add(totalSales));
            vo.setTotalProfit(vo.getTotalProfit().add(totalProfit));
            rankMap.put(d.getGoodsName(), vo);
        }

        return rankMap.values().stream()
                .sorted((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()))
                .limit(50).collect(Collectors.toList());
    }

    // ==========================================
    // 🌟 终极版：绝对自信的实体直接抓取法
    // ==========================================
    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    public ShiftHandoverVO getShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime shiftStart = LocalDateTime.parse(startTime, dtf);
        LocalDateTime now = LocalDateTime.now();

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));
        vo.setCashierName(cashierName != null ? cashierName : "当前当班收银员");

        // 1. 实收对账 & 会员余额支付 (提取支付流水表)
        List<OmsOrderPay> shiftPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, shiftStart).le(OmsOrderPay::getCreateTime, now));

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        for (OmsOrderPay pay : shiftPays) {
            if (pay.getPayMethodCode() != null) {
                if (pay.getPayMethodCode().contains("CASH")) {
                    cashPay = cashPay.add(pay.getPayAmount());
                } else if (pay.getPayMethodCode().contains("AGGREGATE") || pay.getPayMethodCode().contains("WXPAY") || pay.getPayMethodCode().contains("ALIPAY")) {
                    scanPay = scanPay.add(pay.getPayAmount());
                } else if (pay.getPayMethodCode().contains("BALANCE")) {
                    balancePay = balancePay.add(pay.getPayAmount());
                }
            }
        }
        vo.setCashPay(cashPay);
        vo.setScanPay(scanPay);
        vo.setBalancePay(balancePay);

        // 2. 抓取主订单：直接读取数据库精准持久化的金额
        List<OmsOrder> shiftOrders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, shiftStart).le(OmsOrder::getPaymentTime, now)
                .eq(OmsOrder::getStatus, "PAID"));

        BigDecimal manualDiscount = BigDecimal.ZERO;
        BigDecimal totalVoucherDiscount = BigDecimal.ZERO;
        BigDecimal totalMemberCoupon = BigDecimal.ZERO;
        Integer totalVoucherCount = 0;

        Map<String, BrandContributionVO> brandMap = new HashMap<>();

        if (!shiftOrders.isEmpty()) {
            List<String> orderNos = shiftOrders.stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());

            // 🌟 直接信任底层数据：提取整单优惠、满减券总抵扣、会员券总耗
            for (OmsOrder order : shiftOrders) {
                if (order.getManualDiscountAmount() != null) {
                    manualDiscount = manualDiscount.add(order.getManualDiscountAmount());
                }
                if (order.getUseVoucherAmount() != null) {
                    totalVoucherDiscount = totalVoucherDiscount.add(order.getUseVoucherAmount());
                }
                if (order.getCouponAmount() != null) {
                    totalMemberCoupon = totalMemberCoupon.add(order.getCouponAmount());
                }
            }

            // 仅为获取券的张数去查一下满减券表
            totalVoucherCount = Math.toIntExact(posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getStatus, "USED")
                    .in(PosMemberCoupon::getOrderNo, orderNos)));

            // 3. 动态回查品牌贡献矩阵 (提取精准的单品营业额与单品会员券耗)
            List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>()
                    .in(OmsOrderDetail::getOrderNo, orderNos));

            Map<Long, String> brandNameCache = new HashMap<>();

            for (OmsOrderDetail d : details) {
                String brandName = "未知品牌";
                if (d.getGoodsId() != null) {
                    if (brandNameCache.containsKey(d.getGoodsId())) {
                        brandName = brandNameCache.get(d.getGoodsId());
                    } else {
                        GmsGoods goods = gmsGoodsService.getById(d.getGoodsId());
                        if (goods != null && goods.getBrandId() != null) {
                            GmsBrand brand = gmsBrandService.getById(goods.getBrandId());
                            if (brand != null && brand.getName() != null) {
                                brandName = brand.getName();
                            }
                        }
                        brandNameCache.put(d.getGoodsId(), brandName);
                    }
                }

                BigDecimal qty = new BigDecimal(d.getQuantity() != null ? d.getQuantity() : 0);

                // 单品营业额 = 实际售价 * 数量 (OmsOrderDetail 里的 goodsPrice 就是最终单价)
                BigDecimal itemRevenue = BigDecimal.ZERO;
                if (d.getGoodsPrice() != null) {
                    itemRevenue = d.getGoodsPrice().multiply(qty);
                }

                // 单品会员券消耗 = 单品券额 * 数量 (OmsOrderDetail 里的 coupon 就是单品券耗)
                BigDecimal itemCoupon = BigDecimal.ZERO;
                if (d.getCoupon() != null) {
                    itemCoupon = d.getCoupon().multiply(qty);
                }

                BrandContributionVO bvo = brandMap.getOrDefault(brandName, new BrandContributionVO(brandName, BigDecimal.ZERO, BigDecimal.ZERO));
                bvo.setRevenue(bvo.getRevenue().add(itemRevenue));
                bvo.setCouponConsumption(bvo.getCouponConsumption().add(itemCoupon));
                brandMap.put(brandName, bvo);
            }
        }

        vo.setManualDiscount(manualDiscount);
        vo.setVoucherDiscount(totalVoucherDiscount);
        vo.setVoucherCount(totalVoucherCount);
        vo.setMemberCouponPay(totalMemberCoupon);

        List<BrandContributionVO> brandMatrixList = new ArrayList<>(brandMap.values());
        brandMatrixList.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));
        vo.setBrandMatrix(brandMatrixList);
        vo.setExpectedTotalIncome(cashPay.add(scanPay));

        return vo;
    }

    // ... [保留 campaign-review 接口不变] ...
    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    public List<CampaignReviewVO> getCampaignReview() {
        List<PosMemberCoupon> usedCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getStatus, "USED"));

        if (usedCoupons.isEmpty()) return new ArrayList<>();

        List<String> orderNos = usedCoupons.stream().map(PosMemberCoupon::getOrderNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<OmsOrder> campaignOrders = orderNos.isEmpty() ? new ArrayList<>() : omsOrderService.list(new LambdaQueryWrapper<OmsOrder>().in(OmsOrder::getOrderNo, orderNos));
        Map<String, OmsOrder> orderMap = campaignOrders.stream().collect(Collectors.toMap(OmsOrder::getOrderNo, o -> o));

        List<PosCouponRule> allRules = posCouponRuleMapper.selectList(null);
        Map<Long, PosCouponRule> ruleMap = allRules.stream().collect(Collectors.toMap(PosCouponRule::getId, r -> r));

        Map<Long, CampaignReviewVO> reviewMap = new HashMap<>();

        for (PosMemberCoupon uc : usedCoupons) {
            Long ruleId = uc.getRuleId();
            PosCouponRule rule = ruleMap.get(ruleId);
            if (rule == null) continue;

            CampaignReviewVO vo = reviewMap.getOrDefault(ruleId, new CampaignReviewVO(rule.getName(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            vo.setUsedCount(vo.getUsedCount() + 1);
            vo.setTotalDiscountGived(vo.getTotalDiscountGived().add(rule.getDiscountAmount()));

            OmsOrder order = orderMap.get(uc.getOrderNo());
            if (order != null && !vo.getTrackedOrderNos().contains(order.getOrderNo())) {
                vo.setTotalRevenueBrought(vo.getTotalRevenueBrought().add(order.getFinalSalesAmount() != null ? order.getFinalSalesAmount() : BigDecimal.ZERO));
                vo.getTrackedOrderNos().add(order.getOrderNo());
            }
            reviewMap.put(ruleId, vo);
        }

        List<CampaignReviewVO> result = new ArrayList<>(reviewMap.values());
        for (CampaignReviewVO vo : result) {
            if (vo.getTotalDiscountGived().compareTo(BigDecimal.ZERO) > 0) {
                vo.setRoiMultiplier(vo.getTotalRevenueBrought().divide(vo.getTotalDiscountGived(), 2, RoundingMode.HALF_UP));
            }
        }
        return result.stream().sorted((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier())).collect(Collectors.toList());
    }

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
        private String cashierName;

        private BigDecimal cashPay;
        private BigDecimal scanPay;
        private BigDecimal expectedTotalIncome;

        private BigDecimal balancePay;
        private BigDecimal memberCouponPay;

        private BigDecimal voucherDiscount;
        private Integer voucherCount;
        private BigDecimal manualDiscount;

        private List<BrandContributionVO> brandMatrix;
    }

    @Data
    public static class BrandContributionVO {
        private String brandName;
        private BigDecimal revenue;
        private BigDecimal couponConsumption;
        public BrandContributionVO(String brandName, BigDecimal revenue, BigDecimal couponConsumption) {
            this.brandName = brandName; this.revenue = revenue; this.couponConsumption = couponConsumption;
        }
    }

    @Data
    public static class CampaignReviewVO {
        private String ruleName;
        private Integer usedCount;
        private BigDecimal totalDiscountGived;
        private BigDecimal totalRevenueBrought;
        private BigDecimal roiMultiplier;
        private Set<String> trackedOrderNos = new HashSet<>();
        public CampaignReviewVO(String ruleName, Integer usedCount, BigDecimal totalDiscountGived, BigDecimal totalRevenueBrought, BigDecimal roiMultiplier) {
            this.ruleName = ruleName; this.usedCount = usedCount; this.totalDiscountGived = totalDiscountGived; this.totalRevenueBrought = totalRevenueBrought; this.roiMultiplier = roiMultiplier;
        }
    }
}