package com.money.controller;

import com.money.dto.Finance.FinanceDataVO.*;
import com.money.service.FinanceDashboardService;
import com.money.service.FinanceProfitService; // 🌟 新增专线
import com.money.service.FinanceShiftService;  // 🌟 新增专线
import com.money.service.FinanceRiskService;   // 🌟 新增专线
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Finance", description = "财务大屏及高级报表接口")
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    // 🌟 后厨分家：从原来的 1 个超级大厨，变成了 4 个专职大厨
    private final FinanceDashboardService financeDashboardService; // 专做：大盘数据、渠道分析
    private final FinanceProfitService financeProfitService;       // 专做：利润排行、营销复盘
    private final FinanceShiftService financeShiftService;         // 专做：交班对账
    private final FinanceRiskService financeRiskService;           // 专做：风控雷达

    @Operation(summary = "6.1 财务瀑布流全口径日结大屏")
    @GetMapping("/dashboard")
    public FinanceDashboardVO getDashboardData(@RequestParam(required = false) String date) {
        return financeDashboardService.getDashboardData(date);
    }

    @Operation(summary = "6.2 支付渠道及虚拟抵扣占比分析")
    @GetMapping("/channel-mix")
    public ChannelMixAnalysisVO getChannelMixAnalysis(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeDashboardService.getChannelMixAnalysis(startDate, endDate);
    }

    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    public List<ProfitRankVO> getProfitRanking() {
        return financeProfitService.getProfitRanking(); // 🌟 路由给利润大厨
    }

    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    public List<CampaignReviewVO> getCampaignReview() {
        return financeProfitService.getCampaignReview(); // 🌟 路由给利润大厨
    }

    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    public ShiftHandoverVO getShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        return financeShiftService.getShiftHandover(startTime, cashierName); // 🌟 路由给交班大厨
    }

    @Operation(summary = "6.6 收银风控雷达看板")
    @GetMapping("/risk-control")
    public RiskControlVO getRiskControlData(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeRiskService.getRiskControlData(startDate, endDate); // 🌟 路由给风控大厨
    }
}