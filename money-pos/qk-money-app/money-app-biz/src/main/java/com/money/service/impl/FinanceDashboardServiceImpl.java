package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.entity.*;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.service.OmsOrderDetailService;
import com.money.service.OmsOrderService;
import com.money.service.UmsMemberService;
import com.money.service.GmsGoodsService;
import com.money.service.GmsBrandService;
import com.money.service.FinanceDashboardService;
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
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private final OmsOrderService omsOrderService;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberService umsMemberService;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final OmsOrderDetailService omsOrderDetailService;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final GmsGoodsService gmsGoodsService;
    private final GmsBrandService gmsBrandService;

    @Override
    public FinanceDashboardVO getDashboardData(String date) {
        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = LocalDateTime.of(targetDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(targetDate, LocalTime.MAX);
        LocalDateTime startOf7DaysAgo = LocalDateTime.of(targetDate.minusDays(6), LocalTime.MIN);

        FinanceDashboardVO vo = new FinanceDashboardVO();

        List<OmsOrder> dailyOrders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, startOfDay).le(OmsOrder::getPaymentTime, endOfDay)
                .in(OmsOrder::getStatus, "PAID", "RETURN"));

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal payAmount = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal costAmount = BigDecimal.ZERO;

        for (OmsOrder o : dailyOrders) {
            totalAmount = totalAmount.add(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
            totalDiscount = totalDiscount.add(o.getCouponAmount() != null ? o.getCouponAmount() : BigDecimal.ZERO)
                    .add(o.getUseVoucherAmount() != null ? o.getUseVoucherAmount() : BigDecimal.ZERO)
                    .add(o.getManualDiscountAmount() != null ? o.getManualDiscountAmount() : BigDecimal.ZERO);
            payAmount = payAmount.add(o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO);
            BigDecimal finalSales = o.getFinalSalesAmount() != null ? o.getFinalSalesAmount() : BigDecimal.ZERO;
            refundAmount = refundAmount.add(payAmount).subtract(finalSales);
            costAmount = costAmount.add(o.getCostAmount() != null ? o.getCostAmount() : BigDecimal.ZERO);
        }

        BigDecimal netIncome = payAmount.subtract(refundAmount);
        vo.setTotalAmount(totalAmount);
        vo.setTotalDiscount(totalDiscount);
        vo.setPayAmount(payAmount);
        vo.setRefundAmount(refundAmount);
        vo.setNetIncome(netIncome);
        vo.setGrossProfit(netIncome.subtract(costAmount));

        List<OmsOrderPay> dailyPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOfDay).le(OmsOrderPay::getCreateTime, endOfDay));
        List<UmsMemberLog> dailyRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOfDay).le(UmsMemberLog::getCreateTime, endOfDay)
                .eq(UmsMemberLog::getOperateType, "RECHARGE"));

        BigDecimal scanIncome = BigDecimal.ZERO;
        BigDecimal cashIncome = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        for (OmsOrderPay pay : dailyPays) {
            String code = pay.getPayMethodCode() != null ? pay.getPayMethodCode().toUpperCase() : "";
            BigDecimal amt = pay.getPayAmount() != null ? pay.getPayAmount() : BigDecimal.ZERO;
            if (code.contains("BALANCE")) balancePay = balancePay.add(amt);
            else if (code.contains("CASH")) cashIncome = cashIncome.add(amt);
            else scanIncome = scanIncome.add(amt);
        }

        BigDecimal rechargeAmount = dailyRecharges.stream().map(log -> log.getRealAmount() != null ? log.getRealAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setExternalIncome(scanIncome.add(cashIncome).add(rechargeAmount));
        vo.setTotalDebt(umsMemberService.list().stream().map(UmsMember::getBalance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<PayPieData> pieDataList = new ArrayList<>();
        if (scanIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码流水", scanIncome));
        if (cashIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银流水", cashIncome));
        if (balancePay.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员余额抵扣", balancePay));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员充值入账", rechargeAmount));
        vo.setPayBreakdown(pieDataList);

        List<OmsOrderPay> last7DaysPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOf7DaysAgo).le(OmsOrderPay::getCreateTime, endOfDay));
        List<UmsMemberLog> last7DaysRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOf7DaysAgo).le(UmsMemberLog::getCreateTime, endOfDay).eq(UmsMemberLog::getOperateType, "RECHARGE"));

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendScan = new ArrayList<>(), trendCash = new ArrayList<>(), trendRecharge = new ArrayList<>(), trendTotal = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate d = targetDate.minusDays(i);
            trendDates.add(d.format(formatter));
            BigDecimal dailyScan = last7DaysPays.stream().filter(p -> p.getCreateTime().toLocalDate().equals(d) && p.getPayMethodCode() != null && !p.getPayMethodCode().toUpperCase().contains("BALANCE") && !p.getPayMethodCode().toUpperCase().contains("CASH")).map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dailyCash = last7DaysPays.stream().filter(p -> p.getCreateTime().toLocalDate().equals(d) && p.getPayMethodCode() != null && p.getPayMethodCode().toUpperCase().contains("CASH")).map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dailyRecharge = last7DaysRecharges.stream().filter(r -> r.getCreateTime().toLocalDate().equals(d)).map(r -> r.getRealAmount() != null ? r.getRealAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            trendScan.add(dailyScan); trendCash.add(dailyCash); trendRecharge.add(dailyRecharge); trendTotal.add(dailyScan.add(dailyCash).add(dailyRecharge));
        }
        vo.setTrendDates(trendDates); vo.setTrendScan(trendScan); vo.setTrendCash(trendCash); vo.setTrendRecharge(trendRecharge); vo.setTrendTotal(trendTotal);
        return vo;
    }

    @Override
    public List<ProfitRankVO> getProfitRanking() {
        LocalDateTime startOf30DaysAgo = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);
        List<String> paidOrderNos = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>().ge(OmsOrder::getPaymentTime, startOf30DaysAgo).eq(OmsOrder::getStatus, "PAID")).stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());
        if (paidOrderNos.isEmpty()) return new ArrayList<>();

        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().in(OmsOrderDetail::getOrderNo, paidOrderNos));
        Map<String, ProfitRankVO> rankMap = new HashMap<>();
        for (OmsOrderDetail d : details) {
            BigDecimal qty = new BigDecimal(d.getQuantity());
            BigDecimal unitProfit = d.getGoodsPrice().subtract(d.getPurchasePrice() != null ? d.getPurchasePrice() : BigDecimal.ZERO);
            ProfitRankVO vo = rankMap.getOrDefault(d.getGoodsName(), new ProfitRankVO(d.getGoodsName(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
            vo.setTotalQuantity(vo.getTotalQuantity() + d.getQuantity());
            vo.setTotalSales(vo.getTotalSales().add(d.getGoodsPrice().multiply(qty)));
            vo.setTotalProfit(vo.getTotalProfit().add(unitProfit.multiply(qty)));
            rankMap.put(d.getGoodsName(), vo);
        }
        return rankMap.values().stream().sorted((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit())).limit(50).collect(Collectors.toList());
    }

    @Override
    public ShiftHandoverVO getShiftHandover(String startTime, String cashierName) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime shiftStart = LocalDateTime.parse(startTime, dtf);
        LocalDateTime now = LocalDateTime.now();

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));
        vo.setCashierName(cashierName != null ? cashierName : "当前当班收银员");

        List<OmsOrderPay> shiftPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().ge(OmsOrderPay::getCreateTime, shiftStart).le(OmsOrderPay::getCreateTime, now));
        BigDecimal cashPay = BigDecimal.ZERO, scanPay = BigDecimal.ZERO, balancePay = BigDecimal.ZERO;
        for (OmsOrderPay pay : shiftPays) {
            if (pay.getPayMethodCode() != null) {
                if (pay.getPayMethodCode().contains("CASH")) cashPay = cashPay.add(pay.getPayAmount());
                else if (pay.getPayMethodCode().contains("BALANCE")) balancePay = balancePay.add(pay.getPayAmount());
                else scanPay = scanPay.add(pay.getPayAmount());
            }
        }
        vo.setCashPay(cashPay); vo.setScanPay(scanPay); vo.setBalancePay(balancePay);

        List<OmsOrder> shiftOrders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>().ge(OmsOrder::getPaymentTime, shiftStart).le(OmsOrder::getPaymentTime, now).eq(OmsOrder::getStatus, "PAID"));
        BigDecimal manualDiscount = BigDecimal.ZERO, totalVoucherDiscount = BigDecimal.ZERO, totalMemberCoupon = BigDecimal.ZERO;
        Integer totalVoucherCount = 0;
        Map<String, BrandContributionVO> brandMap = new HashMap<>();

        if (!shiftOrders.isEmpty()) {
            List<String> orderNos = shiftOrders.stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());
            for (OmsOrder order : shiftOrders) {
                if (order.getManualDiscountAmount() != null) manualDiscount = manualDiscount.add(order.getManualDiscountAmount());
                if (order.getUseVoucherAmount() != null) totalVoucherDiscount = totalVoucherDiscount.add(order.getUseVoucherAmount());
                if (order.getCouponAmount() != null) totalMemberCoupon = totalMemberCoupon.add(order.getCouponAmount());
            }
            totalVoucherCount = Math.toIntExact(posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getStatus, "USED").in(PosMemberCoupon::getOrderNo, orderNos)));
            List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().in(OmsOrderDetail::getOrderNo, orderNos));
            Map<Long, String> brandNameCache = new HashMap<>();
            for (OmsOrderDetail d : details) {
                String brandName = "未知品牌";
                if (d.getGoodsId() != null) {
                    if (brandNameCache.containsKey(d.getGoodsId())) brandName = brandNameCache.get(d.getGoodsId());
                    else {
                        GmsGoods goods = gmsGoodsService.getById(d.getGoodsId());
                        if (goods != null && goods.getBrandId() != null) {
                            GmsBrand brand = gmsBrandService.getById(goods.getBrandId());
                            if (brand != null && brand.getName() != null) brandName = brand.getName();
                        }
                        brandNameCache.put(d.getGoodsId(), brandName);
                    }
                }
                BigDecimal qty = new BigDecimal(d.getQuantity() != null ? d.getQuantity() : 0);
                BrandContributionVO bvo = brandMap.getOrDefault(brandName, new BrandContributionVO(brandName, BigDecimal.ZERO, BigDecimal.ZERO));
                bvo.setRevenue(bvo.getRevenue().add(d.getGoodsPrice() != null ? d.getGoodsPrice().multiply(qty) : BigDecimal.ZERO));
                bvo.setCouponConsumption(bvo.getCouponConsumption().add(d.getCoupon() != null ? d.getCoupon().multiply(qty) : BigDecimal.ZERO));
                brandMap.put(brandName, bvo);
            }
        }
        vo.setManualDiscount(manualDiscount); vo.setVoucherDiscount(totalVoucherDiscount); vo.setVoucherCount(totalVoucherCount); vo.setMemberCouponPay(totalMemberCoupon);
        List<BrandContributionVO> brandMatrixList = new ArrayList<>(brandMap.values());
        brandMatrixList.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));
        vo.setBrandMatrix(brandMatrixList); vo.setExpectedTotalIncome(cashPay.add(scanPay));
        return vo;
    }

    @Override
    public List<CampaignReviewVO> getCampaignReview() {
        List<PosMemberCoupon> usedCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getStatus, "USED"));
        if (usedCoupons.isEmpty()) return new ArrayList<>();
        List<String> orderNos = usedCoupons.stream().map(PosMemberCoupon::getOrderNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, OmsOrder> orderMap = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>().in(OmsOrder::getOrderNo, orderNos)).stream().collect(Collectors.toMap(OmsOrder::getOrderNo, o -> o));
        Map<Long, PosCouponRule> ruleMap = posCouponRuleMapper.selectList(null).stream().collect(Collectors.toMap(PosCouponRule::getId, r -> r));
        Map<Long, CampaignReviewVO> reviewMap = new HashMap<>();

        for (PosMemberCoupon uc : usedCoupons) {
            PosCouponRule rule = ruleMap.get(uc.getRuleId());
            if (rule == null) continue;
            CampaignReviewVO vo = reviewMap.getOrDefault(uc.getRuleId(), new CampaignReviewVO(rule.getName(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            vo.setUsedCount(vo.getUsedCount() + 1);
            vo.setTotalDiscountGived(vo.getTotalDiscountGived().add(rule.getDiscountAmount()));
            OmsOrder order = orderMap.get(uc.getOrderNo());
            if (order != null && !vo.getTrackedOrderNos().contains(order.getOrderNo())) {
                vo.setTotalRevenueBrought(vo.getTotalRevenueBrought().add(order.getFinalSalesAmount() != null ? order.getFinalSalesAmount() : BigDecimal.ZERO));
                vo.getTrackedOrderNos().add(order.getOrderNo());
            }
            reviewMap.put(uc.getRuleId(), vo);
        }
        List<CampaignReviewVO> result = new ArrayList<>(reviewMap.values());
        for (CampaignReviewVO vo : result) if (vo.getTotalDiscountGived().compareTo(BigDecimal.ZERO) > 0) vo.setRoiMultiplier(vo.getTotalRevenueBrought().divide(vo.getTotalDiscountGived(), 2, RoundingMode.HALF_UP));
        return result.stream().sorted((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier())).collect(Collectors.toList());
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 1. 捞取时间段内的所有支付流水 (计算 现金、扫码、余额)
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startTime).le(OmsOrderPay::getCreateTime, endTime));

        // 2. 捞取时间段内的所有已支付订单 (计算 券抵扣、满减抵扣)
        List<OmsOrder> orders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getPaymentTime, startTime).le(OmsOrder::getPaymentTime, endTime)
                .in(OmsOrder::getStatus, "PAID", "RETURN"));

        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();
        List<String> dates = new ArrayList<>();
        List<BigDecimal> scanList = new ArrayList<>();
        List<BigDecimal> cashList = new ArrayList<>();
        List<BigDecimal> balanceList = new ArrayList<>();
        List<BigDecimal> couponList = new ArrayList<>();
        List<BigDecimal> voucherList = new ArrayList<>();

        BigDecimal totalScan = BigDecimal.ZERO, totalCash = BigDecimal.ZERO, totalBalance = BigDecimal.ZERO;
        BigDecimal totalCoupon = BigDecimal.ZERO, totalVoucher = BigDecimal.ZERO;

        // 🌟 核心：按日期逐天循环，确保 X 轴严格对齐，即使某天没营业也补 0
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));

            // 计算该日的真金流水
            BigDecimal dScan = BigDecimal.ZERO, dCash = BigDecimal.ZERO, dBalance = BigDecimal.ZERO;
            for (OmsOrderPay p : pays) {
                if (p.getCreateTime().toLocalDate().equals(currentDate)) {
                    String code = p.getPayMethodCode() != null ? p.getPayMethodCode().toUpperCase() : "";
                    BigDecimal amt = p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO;
                    if (code.contains("BALANCE")) dBalance = dBalance.add(amt);
                    else if (code.contains("CASH")) dCash = dCash.add(amt);
                    else dScan = dScan.add(amt);
                }
            }
            scanList.add(dScan); totalScan = totalScan.add(dScan);
            cashList.add(dCash); totalCash = totalCash.add(dCash);
            balanceList.add(dBalance); totalBalance = totalBalance.add(dBalance);

            // 计算该日的虚拟抵扣
            BigDecimal dCoupon = BigDecimal.ZERO, dVoucher = BigDecimal.ZERO;
            for (OmsOrder o : orders) {
                if (o.getPaymentTime().toLocalDate().equals(currentDate)) {
                    dCoupon = dCoupon.add(o.getCouponAmount() != null ? o.getCouponAmount() : BigDecimal.ZERO);
                    dVoucher = dVoucher.add(o.getUseVoucherAmount() != null ? o.getUseVoucherAmount() : BigDecimal.ZERO);
                }
            }
            couponList.add(dCoupon); totalCoupon = totalCoupon.add(dCoupon);
            voucherList.add(dVoucher); totalVoucher = totalVoucher.add(dVoucher);
        }

        vo.setTrendDates(dates);
        vo.setScanList(scanList); vo.setCashList(cashList); vo.setBalanceList(balanceList);
        vo.setCouponList(couponList); vo.setVoucherList(voucherList);

        // 装配饼图
        List<PayPieData> pieDataList = new ArrayList<>();
        if (totalScan.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码(真金)", totalScan));
        if (totalCash.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银(真金)", totalCash));
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("余额消耗(预收)", totalBalance));
        if (totalCoupon.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("单品会员券(让利)", totalCoupon));
        if (totalVoucher.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("整单满减券(让利)", totalVoucher));
        vo.setPieData(pieDataList);

        return vo;
    }

    // ... [上方原有的其他方法保留，直接在类末尾追加以下方法] ...

    @Override
    public RiskControlVO getRiskControlData(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<OmsOrder> orders = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getCreateTime, startTime).le(OmsOrder::getCreateTime, endTime));

        RiskControlVO vo = new RiskControlVO();
        int abnormalOrderCount = 0;
        BigDecimal totalLossAmount = BigDecimal.ZERO;
        BigDecimal totalManualDiscount = BigDecimal.ZERO;
        int totalRefundCount = 0;

        Map<String, CashierRiskVO> cashierMap = new HashMap<>();
        List<AbnormalOrderVO> abnormalOrders = new ArrayList<>();

        for (OmsOrder o : orders) {
            String cashier = (o.getCreateBy() != null && !o.getCreateBy().isEmpty()) ? o.getCreateBy() : "门店收银";
            CashierRiskVO cashierVO = cashierMap.getOrDefault(cashier, new CashierRiskVO(cashier, 0, BigDecimal.ZERO, 0));
            cashierVO.setOrderCount(cashierVO.getOrderCount() + 1);

            BigDecimal payAmount = o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO;
            BigDecimal costAmount = o.getCostAmount() != null ? o.getCostAmount() : BigDecimal.ZERO;
            BigDecimal manualDiscount = o.getManualDiscountAmount() != null ? o.getManualDiscountAmount() : BigDecimal.ZERO;
            BigDecimal profit = payAmount.subtract(costAmount);

            boolean isAbnormal = false;
            String riskType = "";

            // 1. 负毛利或0成本探针
            if (payAmount.compareTo(BigDecimal.ZERO) > 0 && costAmount.compareTo(BigDecimal.ZERO) == 0) {
                isAbnormal = true;
                riskType = "缺失成本预警";
                abnormalOrderCount++;
            } else if (profit.compareTo(BigDecimal.ZERO) < 0 && payAmount.compareTo(BigDecimal.ZERO) > 0) {
                isAbnormal = true;
                riskType = "倒挂亏损交易";
                abnormalOrderCount++;
                totalLossAmount = totalLossAmount.add(profit.abs());
            }

            // 2. 手工大额放水探针
            if (manualDiscount.compareTo(BigDecimal.ZERO) > 0) {
                totalManualDiscount = totalManualDiscount.add(manualDiscount);
                cashierVO.setManualDiscountAmount(cashierVO.getManualDiscountAmount().add(manualDiscount));
                if (!isAbnormal && manualDiscount.compareTo(new BigDecimal("50")) > 0) { // 改价超50元也视为异常
                    isAbnormal = true;
                    riskType = "大额手工放水";
                }
            }

            // 3. 退款频次探针
            if ("RETURN".equals(o.getStatus()) || "REFUNDED".equals(o.getStatus())) {
                totalRefundCount++;
                cashierVO.setRefundCount(cashierVO.getRefundCount() + 1);
            }

            if (isAbnormal) {
                AbnormalOrderVO ab = new AbnormalOrderVO();
                ab.setOrderNo(o.getOrderNo());
                ab.setCreateTime(o.getCreateTime() != null ? o.getCreateTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "");
                ab.setCashier(cashier);
                ab.setPayAmount(payAmount);
                ab.setCostAmount(costAmount);
                ab.setProfit(profit);
                ab.setRiskType(riskType);
                abnormalOrders.add(ab);
            }
            cashierMap.put(cashier, cashierVO);
        }

        vo.setAbnormalOrderCount(abnormalOrderCount);
        vo.setTotalLossAmount(totalLossAmount);
        vo.setTotalManualDiscount(totalManualDiscount);
        vo.setTotalRefundCount(totalRefundCount);

        // 收银员按改价金额倒序（黑榜榜首）
        List<CashierRiskVO> cashierList = new ArrayList<>(cashierMap.values());
        cashierList.sort((a, b) -> b.getManualDiscountAmount().compareTo(a.getManualDiscountAmount()));
        vo.setCashierRiskList(cashierList);

        // 异常订单按亏损程度排序
        abnormalOrders.sort((a, b) -> a.getProfit().compareTo(b.getProfit()));
        vo.setRecentAbnormalOrders(abnormalOrders.size() > 50 ? abnormalOrders.subList(0, 50) : abnormalOrders);

        return vo;
    }
}