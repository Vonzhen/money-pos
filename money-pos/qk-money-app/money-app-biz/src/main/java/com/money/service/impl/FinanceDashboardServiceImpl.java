package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.InventoryDocTypeEnum;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.entity.*;
import com.money.mapper.FinanceReportMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.service.FinanceDashboardService;
import com.money.service.UmsMemberService;
// 🌟 引入全局异常处理类
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberService umsMemberService;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;
    private final FinanceReportMapper financeReportMapper;

    private BigDecimal null2Zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseAmt(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            log.error("💥 核心财务报表数据解析异常! 出现疑似脏数据的值: [{}]", val, e);
            throw new BaseException("财务数据严重异常：发现非法金额格式 [" + val + "]，为防止账目错乱，报表已熔断，请联系管理员！");
        }
    }

    @Override
    public AssetDashboardVO getAssetDashboard() {
        AssetDashboardVO dashboard = financeReportMapper.getTodayAssetSummary();
        if (dashboard == null) {
            dashboard = new AssetDashboardVO();
            dashboard.setTodayRealCash(BigDecimal.ZERO);
            dashboard.setTodayWaivedAmount(BigDecimal.ZERO);
            dashboard.setTodayAssetDeduct(BigDecimal.ZERO);
        }

        Map<String, Object> composition = financeReportMapper.getAssetComposition();
        if (composition != null) {
            BigDecimal principal = parseAmt(composition.get("totalPrincipal"));
            BigDecimal gift = parseAmt(composition.get("totalGift"));
            BigDecimal total = principal.add(gift);
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

        List<Map<String, Object>> dailyNetPays = omsOrderPayMapper.getDailyPaySummary(startOfDay, endOfDay);

        List<UmsMemberLog> dailyRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOfDay).le(UmsMemberLog::getCreateTime, endOfDay)
                .in(UmsMemberLog::getOperateType, "RECHARGE", "REVERSAL"));

        BigDecimal scanIncomeTotal = BigDecimal.ZERO, cashIncome = BigDecimal.ZERO, balancePay = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (Map<String, Object> payMap : dailyNetPays) {
            BigDecimal amt = parseAmt(payMap.get("netAmount"));
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;

            String methodCode = (String) payMap.get("methodCode");
            PayMethodEnum method = PayMethodEnum.fromCode(methodCode);
            if (method == null) method = PayMethodEnum.AGGREGATE;

            if (method == PayMethodEnum.BALANCE) balancePay = balancePay.add(amt);
            else if (method == PayMethodEnum.CASH) cashIncome = cashIncome.add(amt);
            else {
                scanIncomeTotal = scanIncomeTotal.add(amt);
                String rawTag = (String) payMap.get("payTag");
                rawTag = (rawTag != null && !rawTag.trim().isEmpty()) ? rawTag : "UNKNOWN";
                scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
            }
        }

        BigDecimal rechargeAmount = dailyRecharges.stream().map(log -> null2Zero(log.getRealAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ==========================================
        // 🌟 终极修复：直接将底层的净扫码金额和现金金额相加！绝对禁止重复按比例扣减退款！
        // ==========================================
        BigDecimal externalPayTotal = scanIncomeTotal.add(cashIncome);
        BigDecimal realExternalIncome = externalPayTotal.add(rechargeAmount);
        vo.setExternalIncome(realExternalIncome);

        List<Object> balanceObjs = umsMemberService.listObjs(new LambdaQueryWrapper<UmsMember>().select(UmsMember::getBalance).isNotNull(UmsMember::getBalance));
        vo.setTotalDebt(balanceObjs.stream().map(obj -> (BigDecimal) obj).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
        }
        if (scanTagMap.isEmpty() && scanIncomeTotal.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码流水", scanIncomeTotal));
        if (cashIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银流水", cashIncome));
        if (balancePay.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员余额抵扣", balancePay));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("充值/买券净收款", rechargeAmount));
        vo.setPayBreakdown(pieDataList);

        // ==================================================
        // 🌟 7天趋势图
        // ==================================================
        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startOf7DaysAgo, endOfDay);

        List<Map<String, Object>> rechargeSummary = umsMemberLogMapper.selectMaps(new QueryWrapper<UmsMemberLog>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(real_amount) AS totalAmt")
                .ge("create_time", startOf7DaysAgo).le("create_time", endOfDay)
                .in("operate_type", "RECHARGE", "REVERSAL")
                .groupBy("DATE(create_time)"));

        List<Map<String, Object>> dailyOrderStats = omsOrderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(pay_amount) AS dailyGrossPay", "SUM(final_sales_amount) AS dailyNetPay")
                .ge("create_time", startOf7DaysAgo).le("create_time", endOfDay)
                .in("status", OrderStatusEnum.getValidFinancialStatus())
                .groupBy("DATE(create_time)"));

        Map<String, BigDecimal> historyRefundMap = new HashMap<>();
        for (Map<String, Object> stat : dailyOrderStats) {
            String dStr = String.valueOf(stat.get("dateStr"));
            BigDecimal dGross = parseAmt(stat.get("dailyGrossPay"));
            BigDecimal dNet = parseAmt(stat.get("dailyNetPay"));
            BigDecimal dRefund = dGross.subtract(dNet);
            if (dRefund.compareTo(BigDecimal.ZERO) < 0) dRefund = BigDecimal.ZERO;
            historyRefundMap.put(dStr, dRefund);
        }

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
        List<BigDecimal> trendRefund = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate d = targetDate.minusDays(i);
            String matchDateStr = d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            trendDates.add(d.format(formatter));
            trendRefund.add(historyRefundMap.getOrDefault(matchDateStr, BigDecimal.ZERO));

            BigDecimal dailyScan = BigDecimal.ZERO, dailyCash = BigDecimal.ZERO;
            Map<String, BigDecimal> dailyTagAmt = new HashMap<>();

            for(Map<String, Object> r : paySummary){
                if(matchDateStr.equals(String.valueOf(r.get("dateStr")))){
                    BigDecimal amt = parseAmt(r.get("netAmount"));
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

            for (String tag : allTags) {
                dynamicTrendMap.get(tag).add(dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO));
            }

            BigDecimal dailyRecharge = BigDecimal.ZERO;
            for(Map<String, Object> r : rechargeSummary){
                if(matchDateStr.equals(String.valueOf(r.get("dateStr")))) dailyRecharge = dailyRecharge.add(parseAmt(r.get("totalAmt")));
            }

            trendScan.add(dailyScan); trendCash.add(dailyCash); trendRecharge.add(dailyRecharge);
            trendTotal.add(dailyScan.add(dailyCash).add(dailyRecharge));
        }

        vo.setTrendDates(trendDates); vo.setTrendScan(trendScan); vo.setTrendCash(trendCash);
        vo.setTrendRecharge(trendRecharge); vo.setTrendTotal(trendTotal);
        vo.setTrendRefund(trendRefund);
        vo.setDynamicTrendMap(dynamicTrendMap);

        return vo;
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();

        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<String> trendDates = new ArrayList<>();
        LocalDate temp = start;
        while (!temp.isAfter(end)) {
            trendDates.add(temp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            temp = temp.plusDays(1);
        }
        vo.setTrendDates(trendDates);

        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startTime, endTime);

        List<Map<String, Object>> orderStats = omsOrderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                        "SUM(IFNULL(actual_coupon_deduct, 0)) AS couponAmt",
                        "SUM(IFNULL(use_voucher_amount, 0)) AS voucherAmt")
                .ge("create_time", startTime).le("create_time", endTime)
                .in("status", "PAID", "PARTIAL_REFUNDED", "REFUNDED")
                .groupBy("DATE(create_time)"));

        List<BigDecimal> scanList = new ArrayList<>();
        List<BigDecimal> cashList = new ArrayList<>();
        List<BigDecimal> balanceList = new ArrayList<>();
        List<BigDecimal> couponList = new ArrayList<>();
        List<BigDecimal> voucherList = new ArrayList<>();

        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalCoupon = BigDecimal.ZERO;
        BigDecimal totalVoucher = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (String dateStr : trendDates) {

            BigDecimal dailyScan = BigDecimal.ZERO;
            BigDecimal dailyCash = BigDecimal.ZERO;
            BigDecimal dailyBalance = BigDecimal.ZERO;

            for (Map<String, Object> pay : paySummary) {
                if (dateStr.equals(String.valueOf(pay.get("dateStr")))) {
                    BigDecimal amt = parseAmt(pay.get("netAmount"));
                    String methodCode = (String) pay.get("methodCode");
                    PayMethodEnum method = PayMethodEnum.fromCode(methodCode);
                    if (method == null) method = PayMethodEnum.AGGREGATE;

                    if (method == PayMethodEnum.CASH) {
                        dailyCash = dailyCash.add(amt);
                        totalCash = totalCash.add(amt);
                    } else if (method == PayMethodEnum.BALANCE) {
                        dailyBalance = dailyBalance.add(amt);
                        totalBalance = totalBalance.add(amt);
                    } else {
                        dailyScan = dailyScan.add(amt);
                        String tag = (String) pay.get("payTag");
                        tag = (tag != null && !tag.trim().isEmpty()) ? "TAG:" + tag : "TAG:UNKNOWN";
                        scanTagMap.put(tag, scanTagMap.getOrDefault(tag, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            scanList.add(dailyScan);
            cashList.add(dailyCash);
            balanceList.add(dailyBalance);

            BigDecimal dailyCoupon = BigDecimal.ZERO;
            BigDecimal dailyVoucher = BigDecimal.ZERO;
            for (Map<String, Object> stat : orderStats) {
                if (dateStr.equals(String.valueOf(stat.get("dateStr")))) {
                    dailyCoupon = parseAmt(stat.get("couponAmt"));
                    dailyVoucher = parseAmt(stat.get("voucherAmt"));
                }
            }
            couponList.add(dailyCoupon);
            totalCoupon = totalCoupon.add(dailyCoupon);

            voucherList.add(dailyVoucher);
            totalVoucher = totalVoucher.add(dailyVoucher);
        }

        vo.setScanList(scanList);
        vo.setCashList(cashList);
        vo.setBalanceList(balanceList);
        vo.setCouponList(couponList);
        vo.setVoucherList(voucherList);

        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                pieDataList.add(new PayPieData(entry.getKey(), entry.getValue()));
            }
        }
        if (totalCash.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银(真金)", totalCash));
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("余额消耗(预收)", totalBalance));
        if (totalCoupon.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("单品会员券(让利)", totalCoupon));
        if (totalVoucher.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("整单满减券(让利)", totalVoucher));

        vo.setPieData(pieDataList);

        return vo;
    }
}