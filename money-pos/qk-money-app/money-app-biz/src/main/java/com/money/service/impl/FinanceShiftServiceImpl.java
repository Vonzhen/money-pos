package com.money.service.impl;

import com.money.constant.FinancialMetric; // 🌟 引入真理模具
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
import java.util.*;

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
        String cashier = (cashierName != null && !cashierName.trim().isEmpty()) ? cashierName : null;

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));
        vo.setCashierName(cashier != null ? cashier : "全部收银员");

        // --- 1. 支付渠道扫荡 (套用真理模具：create_time + 状态集 + JOIN) ---
        // 🌟 逻辑：直接通过 SQL 算出这一班各渠道收了多少净额
        List<Map<String, Object>> payStats = omsOrderPayMapper.getDailyPaySummary(shiftStart, now);
        // 注意：这里后续可以在 Mapper 增加 cashier 过滤参数，目前先做全局逻辑对齐

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        for (Map<String, Object> stat : payStats) {
            String method = (String) stat.get("methodCode");
            BigDecimal amt = (BigDecimal) stat.get("totalAmount");
            if ("CASH".equals(method)) cashPay = MoneyUtil.add(cashPay, amt);
            else if ("BALANCE".equals(method)) balancePay = MoneyUtil.add(balancePay, amt);
            else scanPay = MoneyUtil.add(scanPay, amt);
        }
        vo.setCashPay(cashPay);
        vo.setScanPay(scanPay);
        vo.setBalancePay(balancePay);

        // --- 2. 优惠与折扣扫荡 (套用真理模具：主表唯一真理公式) ---
        // 🌟 逻辑：不再 list 后累加，直接由 SQL 出结果
        // 此处建议在 OmsOrderMapper 补充一个 getShiftDiscountStats 方法，目前先用核心公式逻辑描述
        vo.setManualDiscount(BigDecimal.ZERO); // TODO: 引用 OmsOrderMapper.getShiftDiscountStats
        vo.setVoucherDiscount(BigDecimal.ZERO);
        vo.setMemberCouponPay(BigDecimal.ZERO);

        // --- 3. 品牌贡献度扫荡 (套用双轨制红线：货值损耗口径) ---
        // 🌟 逻辑：直接调用 OmsOrderDetailMapper 聚合计算，必须扣除 return_quantity
        // 此处直接引用我们之前加固过的 BrandPieData 逻辑变体
        List<BrandContributionVO> brandMatrix = new ArrayList<>();
        // TODO: 调用 omsOrderDetailMapper.getShiftBrandContribution(shiftStart, now, cashier)

        vo.setBrandMatrix(brandMatrix);

        // --- 4. 应收总额勾稽 ---
        // 公式：现金 + 扫码 = 本班次应上缴的实收
        vo.setExpectedTotalIncome(MoneyUtil.add(cashPay, scanPay));

        return vo;
    }
}