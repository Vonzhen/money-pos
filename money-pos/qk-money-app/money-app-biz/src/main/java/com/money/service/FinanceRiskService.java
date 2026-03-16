package com.money.service;

import com.money.dto.Finance.FinanceDataVO.*;

public interface FinanceRiskService {
    RiskControlVO getRiskControlData(String startDate, String endDate);
}