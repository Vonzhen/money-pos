package com.money.service.impl;

import com.money.dto.Finance.FinanceDataVO.*;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.service.FinanceShiftService;
import com.money.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceShiftServiceImpl implements FinanceShiftService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;

    @Override
    public ShiftHandoverVO getShiftHandover(String startTime, String cashierName) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime shiftStart = LocalDateTime.parse(startTime, dtf);
        LocalDateTime now = LocalDateTime.now();
        String cashier = (cashierName != null && !cashierName.trim().isEmpty()) ? cashierName : "全部收银员";

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));
        vo.setCashierName(cashier);

        // =========================================================
        // 🌟 1. 支付渠道扫荡 (动态传入 cashier 隔离账目)
        // =========================================================
        List<Map<String, Object>> payStats = omsOrderPayMapper.getShiftPayStats(shiftStart, now, cashier);

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        for (Map<String, Object> stat : payStats) {
            String method = (String) stat.get("methodCode");
            BigDecimal amt = (BigDecimal) stat.get("totalAmount");
            if ("CASH".equals(method)) {
                cashPay = MoneyUtil.add(cashPay, amt);
            } else if ("BALANCE".equals(method)) {
                balancePay = MoneyUtil.add(balancePay, amt);
            } else {
                // 微信、支付宝统统划入扫码支付
                scanPay = MoneyUtil.add(scanPay, amt);
            }
        }
        vo.setCashPay(cashPay);
        vo.setScanPay(scanPay);
        vo.setBalancePay(balancePay);

        // =========================================================
        // 🌟 2. 优惠与折扣扫荡 (消灭黑洞，全自动提取)
        // =========================================================
        Map<String, BigDecimal> discountStats = omsOrderMapper.getShiftDiscountStats(shiftStart, now, cashier);
        if (discountStats != null) {
            vo.setManualDiscount(discountStats.get("manualDiscount"));
            vo.setVoucherDiscount(discountStats.get("voucherDiscount"));
            vo.setMemberCouponPay(discountStats.get("memberCouponPay"));
        } else {
            vo.setManualDiscount(BigDecimal.ZERO);
            vo.setVoucherDiscount(BigDecimal.ZERO);
            vo.setMemberCouponPay(BigDecimal.ZERO);
        }

        // =========================================================
        // 🌟 3. 品牌贡献度扫荡 (直接挂载)
        // =========================================================
        List<BrandContributionVO> brandMatrix = omsOrderDetailMapper.getShiftBrandContribution(shiftStart, now, cashier);
        vo.setBrandMatrix(brandMatrix);

        // =========================================================
        // 🌟 4. 应收总额勾稽 (现金 + 扫码 = 抽屉里该有的钱)
        // =========================================================
        vo.setExpectedTotalIncome(MoneyUtil.add(cashPay, scanPay));

        return vo;
    }
}