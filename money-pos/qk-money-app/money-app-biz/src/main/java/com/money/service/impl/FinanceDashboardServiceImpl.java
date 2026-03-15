package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.entity.*;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.GmsInventoryDocMapper; // 🌟 新增：引入大一统库存单据 Mapper
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
    private final GmsBrandService gmsBrandService;
    private final GmsInventoryDocMapper gmsInventoryDocMapper; // 🌟 新增：注入库存单据查询能力

    @Override
    public FinanceDashboardVO getDashboardData(String date) {
        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = LocalDateTime.of(targetDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(targetDate, LocalTime.MAX);
        LocalDateTime startOf7DaysAgo = LocalDateTime.of(targetDate.minusDays(6), LocalTime.MIN);

        FinanceDashboardVO vo = new FinanceDashboardVO();

        // 1. 获取当天的订单数据
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

            BigDecimal currentPay = o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO;
            BigDecimal finalSales = o.getFinalSalesAmount() != null ? o.getFinalSalesAmount() : BigDecimal.ZERO;
            BigDecimal currentRefund = currentPay.subtract(finalSales);

            payAmount = payAmount.add(currentPay);
            refundAmount = refundAmount.add(currentRefund);
            costAmount = costAmount.add(o.getCostAmount() != null ? o.getCostAmount() : BigDecimal.ZERO);
        }

        BigDecimal netIncome = payAmount.subtract(refundAmount);
        vo.setTotalAmount(totalAmount);
        vo.setTotalDiscount(totalDiscount);
        vo.setPayAmount(payAmount);
        vo.setRefundAmount(refundAmount);
        vo.setNetIncome(netIncome);

        // 🌟🌟🌟 核心重构：计算包含商品损耗的【真实净利润】 🌟🌟🌟
        BigDecimal salesGrossProfit = netIncome.subtract(costAmount); // 纯卖货的毛利

        // 查出当天的异常库存流失单据 (报损单 OUTBOUND、盘点单 CHECK)
        List<GmsInventoryDoc> inventoryDocs = gmsInventoryDocMapper.selectList(new LambdaQueryWrapper<GmsInventoryDoc>()
                .ge(GmsInventoryDoc::getCreateTime, startOfDay)
                .le(GmsInventoryDoc::getCreateTime, endOfDay)
                .in(GmsInventoryDoc::getDocType, "OUTBOUND", "CHECK"));

        BigDecimal inventoryLoss = BigDecimal.ZERO;
        for (GmsInventoryDoc doc : inventoryDocs) {
            // totalAmount 为负数时代表资产流失（亏损）
            if (doc.getTotalAmount() != null && doc.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
                // 取绝对值，累加到当天的总损耗中
                inventoryLoss = inventoryLoss.add(doc.getTotalAmount().abs());
            }
        }

        // 真实毛利 = 销售毛利 - 非正常资产流失
        vo.setGrossProfit(salesGrossProfit.subtract(inventoryLoss));
        // 🌟🌟🌟 ==================================== 🌟🌟🌟

        // 2. 获取当天的支付流水和会员充值记录
        List<OmsOrderPay> dailyPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOfDay).le(OmsOrderPay::getCreateTime, endOfDay));
        List<UmsMemberLog> dailyRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOfDay).le(UmsMemberLog::getCreateTime, endOfDay)
                .eq(UmsMemberLog::getOperateType, "RECHARGE"));

        BigDecimal scanIncomeTotal = BigDecimal.ZERO;
        BigDecimal cashIncome = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        // 存放动态标签聚合金额的 Map
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (OmsOrderPay pay : dailyPays) {
            String code = pay.getPayMethodCode() != null ? pay.getPayMethodCode().toUpperCase() : "";
            BigDecimal amt = pay.getPayAmount() != null ? pay.getPayAmount() : BigDecimal.ZERO;
            if (code.contains("BALANCE")) {
                balancePay = balancePay.add(amt);
            } else if (code.contains("CASH")) {
                cashIncome = cashIncome.add(amt);
            } else {
                scanIncomeTotal = scanIncomeTotal.add(amt);
                // 不在后端翻译，直接存入带标签的原数据
                String rawTag = pay.getPayTag() != null && !pay.getPayTag().trim().isEmpty() ? pay.getPayTag() : "UNKNOWN";
                scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
            }
        }

        BigDecimal rechargeAmount = dailyRecharges.stream().map(log -> log.getRealAmount() != null ? log.getRealAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setExternalIncome(scanIncomeTotal.add(cashIncome).add(rechargeAmount));

        // 获取全体会员沉淀资金
        vo.setTotalDebt(umsMemberService.list().stream().map(UmsMember::getBalance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        // 3. 组装饼图数据
        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                // 增加 TAG: 前缀，通知前端去翻译
                pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
            }
        }
        if (scanTagMap.isEmpty() && scanIncomeTotal.compareTo(BigDecimal.ZERO) > 0) {
            pieDataList.add(new PayPieData("聚合扫码流水", scanIncomeTotal));
        }
        if (cashIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银流水", cashIncome));
        if (balancePay.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员余额抵扣", balancePay));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员充值入账", rechargeAmount));
        vo.setPayBreakdown(pieDataList);

        // 4. 获取近7天的趋势数据
        List<OmsOrderPay> last7DaysPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOf7DaysAgo).le(OmsOrderPay::getCreateTime, endOfDay));
        List<UmsMemberLog> last7DaysRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOf7DaysAgo).le(UmsMemberLog::getCreateTime, endOfDay).eq(UmsMemberLog::getOperateType, "RECHARGE"));

        // 收集所有出现过的细分支付标签
        Set<String> allTags = last7DaysPays.stream()
                .filter(p -> p.getPayMethodCode() != null && !p.getPayMethodCode().contains("CASH") && !p.getPayMethodCode().contains("BALANCE"))
                .map(p -> p.getPayTag() != null && !p.getPayTag().trim().isEmpty() ? p.getPayTag() : "UNKNOWN")
                .collect(Collectors.toSet());

        Map<String, List<BigDecimal>> dynamicTrendMap = new HashMap<>();
        for (String tag : allTags) dynamicTrendMap.put(tag, new ArrayList<>());

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendScan = new ArrayList<>(), trendCash = new ArrayList<>(), trendRecharge = new ArrayList<>(), trendTotal = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate d = targetDate.minusDays(i);
            trendDates.add(d.format(formatter));

            BigDecimal dailyScan = BigDecimal.ZERO;
            BigDecimal dailyCash = BigDecimal.ZERO;
            Map<String, BigDecimal> dailyTagAmt = new HashMap<>();

            for(OmsOrderPay p : last7DaysPays){
                if(p.getCreateTime().toLocalDate().equals(d) && p.getPayMethodCode() != null){
                    String code = p.getPayMethodCode().toUpperCase();
                    BigDecimal amt = p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO;
                    if(code.contains("CASH")) {
                        dailyCash = dailyCash.add(amt);
                    } else if (!code.contains("BALANCE")){
                        dailyScan = dailyScan.add(amt);
                        String tag = p.getPayTag() != null && !p.getPayTag().trim().isEmpty() ? p.getPayTag() : "UNKNOWN";
                        dailyTagAmt.put(tag, dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO).add(amt));
                    }
                }
            }

            // 将每天的具体标签数据推入对应的列表
            for (String tag : allTags) {
                dynamicTrendMap.get(tag).add(dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO));
            }

            BigDecimal dailyRecharge = BigDecimal.ZERO;
            for(UmsMemberLog r : last7DaysRecharges){
                if(r.getCreateTime().toLocalDate().equals(d)){
                    dailyRecharge = dailyRecharge.add(r.getRealAmount() != null ? r.getRealAmount() : BigDecimal.ZERO);
                }
            }

            trendScan.add(dailyScan); trendCash.add(dailyCash); trendRecharge.add(dailyRecharge); trendTotal.add(dailyScan.add(dailyCash).add(dailyRecharge));
        }
        vo.setTrendDates(trendDates); vo.setTrendScan(trendScan); vo.setTrendCash(trendCash); vo.setTrendRecharge(trendRecharge); vo.setTrendTotal(trendTotal);
        vo.setDynamicTrendMap(dynamicTrendMap);

        return vo;
    }

    @Override
    public List<ProfitRankVO> getProfitRanking() {
        LocalDateTime startOf30DaysAgo = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);

        // 使用限制 5000 条，防止内存溢出
        List<String> paidOrderNos = omsOrderService.list(new LambdaQueryWrapper<OmsOrder>()
                        .select(OmsOrder::getOrderNo)
                        .ge(OmsOrder::getPaymentTime, startOf30DaysAgo)
                        .eq(OmsOrder::getStatus, "PAID")
                        .last("LIMIT 5000"))
                .stream().map(OmsOrder::getOrderNo).collect(Collectors.toList());

        if (paidOrderNos.isEmpty()) return new ArrayList<>();

        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().in(OmsOrderDetail::getOrderNo, paidOrderNos));
        Map<String, ProfitRankVO> rankMap = new HashMap<>();
        for (OmsOrderDetail d : details) {
            BigDecimal qty = new BigDecimal(d.getQuantity());
            // 🌟 利润排行榜：现在这里的 getPurchasePrice 拿到的已经是真实记录下来的移动均价了！
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

            Map<Long, String> brandNameDict = new HashMap<>();
            Set<Long> brandIds = details.stream().map(OmsOrderDetail::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!brandIds.isEmpty()) {
                brandNameDict = gmsBrandService.listByIds(brandIds).stream().collect(Collectors.toMap(GmsBrand::getId, GmsBrand::getName));
            }

            for (OmsOrderDetail d : details) {
                String brandName = "未知品牌";
                if (d.getBrandId() != null && brandNameDict.containsKey(d.getBrandId())) {
                    brandName = brandNameDict.get(d.getBrandId());
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

        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startTime).le(OmsOrderPay::getCreateTime, endTime));

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

        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));

            BigDecimal dScan = BigDecimal.ZERO, dCash = BigDecimal.ZERO, dBalance = BigDecimal.ZERO;
            for (OmsOrderPay p : pays) {
                if (p.getCreateTime().toLocalDate().equals(currentDate)) {
                    String code = p.getPayMethodCode() != null ? p.getPayMethodCode().toUpperCase() : "";
                    BigDecimal amt = p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO;
                    if (code.contains("BALANCE")) dBalance = dBalance.add(amt);
                    else if (code.contains("CASH")) dCash = dCash.add(amt);
                    else {
                        dScan = dScan.add(amt);
                        String rawTag = p.getPayTag() != null && !p.getPayTag().trim().isEmpty() ? p.getPayTag() : "UNKNOWN";
                        scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            scanList.add(dScan); totalScan = totalScan.add(dScan);
            cashList.add(dCash); totalCash = totalCash.add(dCash);
            balanceList.add(dBalance); totalBalance = totalBalance.add(dBalance);

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

        List<PayPieData> pieDataList = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
            }
        }
        if (scanTagMap.isEmpty() && totalScan.compareTo(BigDecimal.ZERO) > 0) {
            pieDataList.add(new PayPieData("聚合扫码(真金)", totalScan));
        }

        if (totalCash.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银(真金)", totalCash));
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("余额消耗(预收)", totalBalance));
        if (totalCoupon.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("单品会员券(让利)", totalCoupon));
        if (totalVoucher.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("整单满减券(让利)", totalVoucher));
        vo.setPieData(pieDataList);

        return vo;
    }

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

            if (payAmount.compareTo(new BigDecimal("10")) >= 0 && costAmount.compareTo(BigDecimal.ZERO) == 0) {
                isAbnormal = true;
                riskType = "缺失成本预警";
                abnormalOrderCount++;
            } else if (profit.compareTo(BigDecimal.ZERO) < 0 && payAmount.compareTo(BigDecimal.ZERO) > 0) {
                isAbnormal = true;
                riskType = "倒挂亏损交易";
                abnormalOrderCount++;
                totalLossAmount = totalLossAmount.add(profit.abs());
            }

            if (manualDiscount.compareTo(BigDecimal.ZERO) > 0) {
                totalManualDiscount = totalManualDiscount.add(manualDiscount);
                cashierVO.setManualDiscountAmount(cashierVO.getManualDiscountAmount().add(manualDiscount));
                if (!isAbnormal && manualDiscount.compareTo(new BigDecimal("50")) > 0) {
                    isAbnormal = true;
                    riskType = "大额手工放水";
                }
            }

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

        List<CashierRiskVO> cashierList = new ArrayList<>(cashierMap.values());
        cashierList.sort((a, b) -> b.getManualDiscountAmount().compareTo(a.getManualDiscountAmount()));
        vo.setCashierRiskList(cashierList);

        abnormalOrders.sort((a, b) -> a.getProfit().compareTo(b.getProfit()));
        vo.setRecentAbnormalOrders(abnormalOrders.size() > 50 ? abnormalOrders.subList(0, 50) : abnormalOrders);

        return vo;
    }
}