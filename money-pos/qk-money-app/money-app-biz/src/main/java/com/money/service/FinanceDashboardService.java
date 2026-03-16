package com.money.service;

import com.money.dto.Finance.FinanceDataVO.*;

/**
 * 🌟 财务大屏首页与宏观分析核心服务
 * (已重构：剥离了利润、交班、风控等垂直业务，回归大盘指标统计本职)
 */
public interface FinanceDashboardService {

    /**
     * 6.1 财务瀑布流全口径日结大屏
     */
    FinanceDashboardVO getDashboardData(String date);

    /**
     * 6.2 支付渠道及虚拟抵扣占比分析
     */
    ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate);

}