package com.money.service.impl;

import com.money.dto.Finance.FinanceDataVO.*;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.service.FinanceShiftService;
import com.money.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceShiftServiceImpl implements FinanceShiftService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;

    private BigDecimal parseAmt(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            log.error("💥 交接班金额解析异常! 出现疑似脏数据: [{}]", val, e);
            return BigDecimal.ZERO;
        }
    }

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

        List<Map<String, Object>> payStats = omsOrderPayMapper.getShiftPayStats(shiftStart, now, cashier);

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        // 🌟 新增：扫码通道明细追踪器
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (Map<String, Object> stat : payStats) {
            String method = (String) stat.get("methodCode");
            BigDecimal amt = parseAmt(stat.get("netAmount"));

            if ("CASH".equals(method)) {
                cashPay = MoneyUtil.add(cashPay, amt);
            } else if ("BALANCE".equals(method)) {
                balancePay = MoneyUtil.add(balancePay, amt);
            } else {
                scanPay = MoneyUtil.add(scanPay, amt);

                // 🌟 提取扫码标签进行分类汇总
                String tag = (String) stat.get("payTag");
                // 防止数据库字段别名为下划线格式
                if (tag == null) tag = (String) stat.get("pay_tag");

                tag = (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
                scanTagMap.put(tag, scanTagMap.getOrDefault(tag, BigDecimal.ZERO).add(amt));
            }
        }
        vo.setCashPay(cashPay);
        vo.setScanPay(scanPay);
        vo.setBalancePay(balancePay);

        // 🌟 组装扫码明细列表给前端
        List<PayPieData> scanBreakdownList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                scanBreakdownList.add(new PayPieData(entry.getKey(), entry.getValue()));
            }
        }
        vo.setScanPayBreakdown(scanBreakdownList);

        Map<String, Object> discountStats = omsOrderMapper.getShiftDiscountStats(shiftStart, now, cashier);
        BigDecimal refundAmount = BigDecimal.ZERO;
        if (discountStats != null) {
            vo.setManualDiscount(parseAmt(discountStats.get("manualDiscount")));
            vo.setVoucherDiscount(parseAmt(discountStats.get("voucherDiscount")));
            vo.setMemberCouponPay(parseAmt(discountStats.get("memberCouponPay")));
            vo.setWaivedCouponAmount(parseAmt(discountStats.get("waivedCouponAmount")));
            vo.setVoucherCount(parseAmt(discountStats.get("voucherCount")).intValue());
            refundAmount = parseAmt(discountStats.get("refundAmount"));
        } else {
            vo.setManualDiscount(BigDecimal.ZERO);
            vo.setVoucherDiscount(BigDecimal.ZERO);
            vo.setMemberCouponPay(BigDecimal.ZERO);
            vo.setWaivedCouponAmount(BigDecimal.ZERO);
            vo.setVoucherCount(0);
        }
        vo.setRefundAmount(refundAmount);

        BigDecimal grossTotal = cashPay.add(scanPay).add(balancePay);
        vo.setNetIncome(grossTotal.subtract(refundAmount));
        vo.setExpectedTotalIncome(MoneyUtil.add(cashPay, scanPay));

        List<BrandContributionVO> brandMatrix = omsOrderDetailMapper.getShiftBrandContribution(shiftStart, now, cashier);
        vo.setBrandMatrix(brandMatrix);

        return vo;
    }
}