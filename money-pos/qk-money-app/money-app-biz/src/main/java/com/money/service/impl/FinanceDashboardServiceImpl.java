package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.InventoryDocTypeEnum;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.entity.*;
import com.money.mapper.FinanceReportMapper; // 🌟 8.1新增导入：资产报表Mapper
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.service.FinanceDashboardService;
import com.money.service.UmsMemberService;
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

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberService umsMemberService;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;

    // 🌟 8.1新增注入：专门查资产溯源底座的 Mapper
    private final FinanceReportMapper financeReportMapper;

    private BigDecimal null2Zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseAmt(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(val)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    // ==========================================
    // 🌟 8.1 核心新增实现：资产驾驶舱 (修复空指针防弹版)
    // ==========================================
    @Override
    public AssetDashboardVO getAssetDashboard() {
        // 1. 获取今日汇总
        AssetDashboardVO dashboard = financeReportMapper.getTodayAssetSummary();
        if (dashboard == null) {
            dashboard = new AssetDashboardVO();
            dashboard.setTodayRealCash(BigDecimal.ZERO);
            dashboard.setTodayWaivedAmount(BigDecimal.ZERO);
            dashboard.setTodayAssetDeduct(BigDecimal.ZERO);
        }

        // 2. 计算资产存量占比（穿透分析）
        Map<String, Object> composition = financeReportMapper.getAssetComposition();
        if (composition != null) {
            // 🌟 抢修点：使用 parseAmt 完美拦截数据库的 NULL 值，防止空指针崩溃！
            BigDecimal principal = parseAmt(composition.get("totalPrincipal"));
            BigDecimal gift = parseAmt(composition.get("totalGift"));
            BigDecimal total = principal.add(gift);

            // 严谨的算数处理
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                dashboard.setPrincipalRatio(principal.multiply(new BigDecimal(100)).divide(total, 2, RoundingMode.HALF_UP));
                dashboard.setGiftRatio(gift.multiply(new BigDecimal(100)).divide(total, 2, RoundingMode.HALF_UP));
            } else {
                dashboard.setPrincipalRatio(BigDecimal.ZERO);
                dashboard.setGiftRatio(BigDecimal.ZERO);
            }
        } else {
            dashboard.setPrincipalRatio(BigDecimal.ZERO);
            dashboard.setGiftRatio(BigDecimal.ZERO);
        }

        return dashboard;
    }

    // ==========================================
    // 下方为您原有的代码，原封不动保留
    // ==========================================
    @Override
    public FinanceDashboardVO getDashboardData(String date) {
        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = LocalDateTime.of(targetDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(targetDate, LocalTime.MAX);
        LocalDateTime startOf7DaysAgo = LocalDateTime.of(targetDate.minusDays(6), LocalTime.MIN);

        FinanceDashboardVO vo = new FinanceDashboardVO();

        List<OmsOrder> dailyOrders = omsOrderMapper.selectList(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getCreateTime, startOfDay).le(OmsOrder::getCreateTime, endOfDay)
                .in(OmsOrder::getStatus, OrderStatusEnum.getValidFinancialStatus()));

        BigDecimal totalAmount = BigDecimal.ZERO, totalDiscount = BigDecimal.ZERO;
        BigDecimal payAmount = BigDecimal.ZERO, refundAmount = BigDecimal.ZERO, costAmount = BigDecimal.ZERO;

        for (OmsOrder o : dailyOrders) {
            totalAmount = totalAmount.add(null2Zero(o.getTotalAmount()));
            totalDiscount = totalDiscount.add(null2Zero(o.getCouponAmount())).add(null2Zero(o.getUseVoucherAmount())).add(null2Zero(o.getManualDiscountAmount()));
            BigDecimal currentPay = null2Zero(o.getPayAmount());
            payAmount = payAmount.add(currentPay);
            refundAmount = refundAmount.add(currentPay.subtract(null2Zero(o.getFinalSalesAmount())));
            costAmount = costAmount.add(null2Zero(o.getCostAmount()));
        }

        BigDecimal netIncome = payAmount.subtract(refundAmount);
        vo.setTotalAmount(totalAmount); vo.setTotalDiscount(totalDiscount);
        vo.setPayAmount(payAmount); vo.setRefundAmount(refundAmount); vo.setNetIncome(netIncome);

        BigDecimal salesGrossProfit = netIncome.subtract(costAmount);
        List<GmsInventoryDoc> inventoryDocs = gmsInventoryDocMapper.selectList(new LambdaQueryWrapper<GmsInventoryDoc>()
                .select(GmsInventoryDoc::getDocType, GmsInventoryDoc::getTotalAmount)
                .ge(GmsInventoryDoc::getCreateTime, startOfDay).le(GmsInventoryDoc::getCreateTime, endOfDay)
                .in(GmsInventoryDoc::getDocType, InventoryDocTypeEnum.OUTBOUND.name(), InventoryDocTypeEnum.CHECK.name()));

        BigDecimal inventoryLoss = BigDecimal.ZERO;
        for (GmsInventoryDoc doc : inventoryDocs) {
            if (doc.getTotalAmount() != null && doc.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) inventoryLoss = inventoryLoss.add(doc.getTotalAmount().abs());
        }
        vo.setGrossProfit(salesGrossProfit.subtract(inventoryLoss));

        List<OmsOrderPay> dailyPays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .ge(OmsOrderPay::getCreateTime, startOfDay).le(OmsOrderPay::getCreateTime, endOfDay));
        List<UmsMemberLog> dailyRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOfDay).le(UmsMemberLog::getCreateTime, endOfDay)
                .eq(UmsMemberLog::getOperateType, "RECHARGE"));

        BigDecimal scanIncomeTotal = BigDecimal.ZERO, cashIncome = BigDecimal.ZERO, balancePay = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (OmsOrderPay pay : dailyPays) {
            BigDecimal amt = null2Zero(pay.getPayAmount());
            PayMethodEnum method = PayMethodEnum.fromCode(pay.getPayMethodCode());
            if (method == null) method = PayMethodEnum.AGGREGATE;

            if (method == PayMethodEnum.BALANCE) balancePay = balancePay.add(amt);
            else if (method == PayMethodEnum.CASH) cashIncome = cashIncome.add(amt);
            else {
                scanIncomeTotal = scanIncomeTotal.add(amt);
                String rawTag = (pay.getPayTag() != null && !pay.getPayTag().trim().isEmpty()) ? pay.getPayTag() : "UNKNOWN";
                scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
            }
        }

        BigDecimal rechargeAmount = dailyRecharges.stream().map(log -> null2Zero(log.getRealAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setExternalIncome(scanIncomeTotal.add(cashIncome).add(rechargeAmount));

        List<Object> balanceObjs = umsMemberService.listObjs(new LambdaQueryWrapper<UmsMember>().select(UmsMember::getBalance).isNotNull(UmsMember::getBalance));
        vo.setTotalDebt(balanceObjs.stream().map(obj -> (BigDecimal) obj).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
        }
        if (scanTagMap.isEmpty() && scanIncomeTotal.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码流水", scanIncomeTotal));
        if (cashIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银流水", cashIncome));
        if (balancePay.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员余额抵扣", balancePay));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员充值入账", rechargeAmount));
        vo.setPayBreakdown(pieDataList);

        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startOf7DaysAgo, endOfDay);
        List<Map<String, Object>> rechargeSummary = umsMemberLogMapper.selectMaps(new QueryWrapper<UmsMemberLog>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(real_amount) AS totalAmt")
                .ge("create_time", startOf7DaysAgo).le("create_time", endOfDay).eq("operate_type", "RECHARGE")
                .groupBy("DATE(create_time)"));

        Set<String> allTags = paySummary.stream().filter(r -> {
            PayMethodEnum m = PayMethodEnum.fromCode((String) r.get("methodCode"));
            return m != PayMethodEnum.CASH && m != PayMethodEnum.BALANCE;
        }).map(r -> {
            String tag = (String) r.get("payTag");
            return (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
        }).collect(Collectors.toSet());

        Map<String, List<BigDecimal>> dynamicTrendMap = new HashMap<>();
        for (String tag : allTags) dynamicTrendMap.put(tag, new ArrayList<>());

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendScan = new ArrayList<>(), trendCash = new ArrayList<>(), trendRecharge = new ArrayList<>(), trendTotal = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate d = targetDate.minusDays(i);
            String matchDateStr = d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            trendDates.add(d.format(formatter));

            BigDecimal dailyScan = BigDecimal.ZERO, dailyCash = BigDecimal.ZERO;
            Map<String, BigDecimal> dailyTagAmt = new HashMap<>();

            for(Map<String, Object> r : paySummary){
                if(matchDateStr.equals(String.valueOf(r.get("dateStr")))){
                    BigDecimal amt = parseAmt(r.get("totalAmount"));
                    PayMethodEnum method = PayMethodEnum.fromCode((String) r.get("methodCode"));
                    if (method == null) method = PayMethodEnum.AGGREGATE;

                    if (method == PayMethodEnum.CASH) dailyCash = dailyCash.add(amt);
                    else if (method != PayMethodEnum.BALANCE) {
                        dailyScan = dailyScan.add(amt);
                        String tag = (String) r.get("payTag");
                        tag = (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
                        dailyTagAmt.put(tag, dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            for (String tag : allTags) dynamicTrendMap.get(tag).add(dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO));

            BigDecimal dailyRecharge = BigDecimal.ZERO;
            for(Map<String, Object> r : rechargeSummary){
                if(matchDateStr.equals(String.valueOf(r.get("dateStr")))) dailyRecharge = dailyRecharge.add(parseAmt(r.get("totalAmt")));
            }

            trendScan.add(dailyScan); trendCash.add(dailyCash); trendRecharge.add(dailyRecharge);
            trendTotal.add(dailyScan.add(dailyCash).add(dailyRecharge));
        }
        vo.setTrendDates(trendDates); vo.setTrendScan(trendScan); vo.setTrendCash(trendCash); vo.setTrendRecharge(trendRecharge); vo.setTrendTotal(trendTotal);
        vo.setDynamicTrendMap(dynamicTrendMap);

        return vo;
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startTime, endTime);

        List<Map<String, Object>> orderSummary = omsOrderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(coupon_amount) AS totalCoupon", "SUM(use_voucher_amount) AS totalVoucher")
                .ge("create_time", startTime).le("create_time", endTime)
                .in("status", OrderStatusEnum.getValidFinancialStatus())
                .groupBy("DATE(create_time)"));

        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();
        List<String> dates = new ArrayList<>();
        List<BigDecimal> scanList = new ArrayList<>(), cashList = new ArrayList<>(), balanceList = new ArrayList<>();
        List<BigDecimal> couponList = new ArrayList<>(), voucherList = new ArrayList<>();
        BigDecimal totalScan = BigDecimal.ZERO, totalCash = BigDecimal.ZERO, totalBalance = BigDecimal.ZERO;
        BigDecimal totalCoupon = BigDecimal.ZERO, totalVoucher = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String matchDateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));

            BigDecimal dScan = BigDecimal.ZERO, dCash = BigDecimal.ZERO, dBalance = BigDecimal.ZERO;
            for (Map<String, Object> r : paySummary) {
                if (matchDateStr.equals(String.valueOf(r.get("dateStr")))) {
                    BigDecimal amt = parseAmt(r.get("totalAmount"));
                    PayMethodEnum method = PayMethodEnum.fromCode((String) r.get("methodCode"));
                    if (method == null) method = PayMethodEnum.AGGREGATE;

                    if (method == PayMethodEnum.BALANCE) dBalance = dBalance.add(amt);
                    else if (method == PayMethodEnum.CASH) dCash = dCash.add(amt);
                    else {
                        dScan = dScan.add(amt);
                        String rawTag = (String) r.get("payTag");
                        rawTag = (rawTag != null && !rawTag.trim().isEmpty()) ? rawTag : "UNKNOWN";
                        scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            scanList.add(dScan); totalScan = totalScan.add(dScan);
            cashList.add(dCash); totalCash = totalCash.add(dCash);
            balanceList.add(dBalance); totalBalance = totalBalance.add(dBalance);

            BigDecimal dCoupon = BigDecimal.ZERO, dVoucher = BigDecimal.ZERO;
            for (Map<String, Object> r : orderSummary) {
                if (matchDateStr.equals(String.valueOf(r.get("dateStr")))) {
                    dCoupon = dCoupon.add(parseAmt(r.get("totalCoupon")));
                    dVoucher = dVoucher.add(parseAmt(r.get("totalVoucher")));
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
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
        }
        if (scanTagMap.isEmpty() && totalScan.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码(真金)", totalScan));
        if (totalCash.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银(真金)", totalCash));
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("余额消耗(预收)", totalBalance));
        if (totalCoupon.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("单品会员券(让利)", totalCoupon));
        if (totalVoucher.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("整单满减券(让利)", totalVoucher));
        vo.setPieData(pieDataList);

        return vo;
    }
}