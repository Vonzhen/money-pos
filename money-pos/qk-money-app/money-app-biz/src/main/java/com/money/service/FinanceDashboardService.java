package com.money.service;

import com.money.dto.Finance.FinanceDataVO.*;
import java.util.List;

public interface FinanceDashboardService {
    FinanceDashboardVO getDashboardData(String date);
    List<ProfitRankVO> getProfitRanking();
    ShiftHandoverVO getShiftHandover(String startTime, String cashierName);
    List<CampaignReviewVO> getCampaignReview();
    ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate);

    // 🌟 6.6 新增风控雷达
    RiskControlVO getRiskControlData(String startDate, String endDate);
}