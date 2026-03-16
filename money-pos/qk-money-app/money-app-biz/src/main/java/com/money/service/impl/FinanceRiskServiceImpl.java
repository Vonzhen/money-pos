package com.money.service.impl;

import com.money.constant.FinancialMetric; // 🌟 引入真理模具
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.mapper.OmsOrderMapper;
import com.money.service.FinanceRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🌟 风控与异常监控服务 (V6.0 荣耀扫荡版)
 * 彻底消灭 Java 内存循环，实现数据口径像素级对齐
 */
@Service
@RequiredArgsConstructor
public class FinanceRiskServiceImpl implements FinanceRiskService {

    private final OmsOrderMapper omsOrderMapper;

    @Override
    public RiskControlVO getRiskControlData(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        RiskControlVO vo = new RiskControlVO();

        // --- 1. 扫荡收银员风险汇总 (从 Mapper 获取聚合结果) ---
        List<Map<String, Object>> cashierStats = omsOrderMapper.getCashierRiskSummary(startTime, endTime);
        List<CashierRiskVO> cashierList = cashierStats.stream().map(map -> {
                    // 🌟 核心修复：对齐您之前的 4 参数构造函数: (String, Integer, BigDecimal, Integer)
                    return new CashierRiskVO(
                            (String) map.get("cashierName"),
                            ((Number) map.get("orderCount")).intValue(),
                            (BigDecimal) map.get("manualDiscountAmount"),
                            ((Number) map.get("refundCount")).intValue()
                    );
                })
                .sorted((a, b) -> b.getManualDiscountAmount().compareTo(a.getManualDiscountAmount()))
                .collect(Collectors.toList());

        vo.setCashierRiskList(cashierList);

        // --- 2. 扫荡异常订单明细 (利用 SQL 判别异常，不再遍历全表) ---
        List<Map<String, Object>> abnormalRawList = omsOrderMapper.getAbnormalOrderList(startTime, endTime);
        List<AbnormalOrderVO> abnormalOrders = abnormalRawList.stream().map(map -> {
            AbnormalOrderVO ab = new AbnormalOrderVO();
            ab.setOrderNo((String) map.get("orderNo"));
            ab.setCreateTime((String) map.get("createTime"));
            ab.setCashier((String) map.get("cashier"));
            ab.setPayAmount((BigDecimal) map.get("payAmount"));
            ab.setCostAmount((BigDecimal) map.get("costAmount"));
            ab.setProfit((BigDecimal) map.get("profit"));
            ab.setRiskType((String) map.get("riskType"));
            return ab;
        }).collect(Collectors.toList());

        vo.setRecentAbnormalOrders(abnormalOrders);

        // --- 3. 汇总统计数据 ---
        // 🌟 逻辑：汇总数据直接从上述聚合结果中提取，无需二次查库
        vo.setAbnormalOrderCount(abnormalOrders.size());
        vo.setTotalLossAmount(abnormalOrders.stream()
                .filter(o -> o.getProfit().compareTo(BigDecimal.ZERO) < 0)
                .map(o -> o.getProfit().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        vo.setTotalManualDiscount(cashierList.stream()
                .map(CashierRiskVO::getManualDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        vo.setTotalRefundCount(cashierList.stream()
                .mapToInt(CashierRiskVO::getRefundCount)
                .sum());

        return vo;
    }
}