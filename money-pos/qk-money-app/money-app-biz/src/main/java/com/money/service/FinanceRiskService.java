package com.money.service;

import java.util.Map;

public interface FinanceRiskService {
    /**
     * 获取风控雷达综合数据
     */
    Map<String, Object> getRiskSummary(String startDate, String endDate);
}