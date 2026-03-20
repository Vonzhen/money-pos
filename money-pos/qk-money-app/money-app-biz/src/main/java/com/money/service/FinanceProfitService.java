package com.money.service;

import com.money.dto.Finance.FinanceDataVO.*;
import java.util.List;

public interface FinanceProfitService {
    List<ProfitRankVO> getProfitRanking();
    List<CampaignReviewVO> getCampaignReview();
}