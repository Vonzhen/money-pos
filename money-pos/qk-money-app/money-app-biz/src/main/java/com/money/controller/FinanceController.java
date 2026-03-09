package com.money.controller;

import com.money.dto.Finance.FinanceDataVO.*;
import com.money.service.FinanceDashboardService;
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

    // 🌟 仅仅注入一个专用的计算引擎，代码直接暴瘦
    private final FinanceDashboardService financeDashboardService;

    @Operation(summary = "6.1 财务瀑布流全口径日结大屏")
    @GetMapping("/dashboard")
    public FinanceDashboardVO getDashboardData(@RequestParam(required = false) String date) {
        // 前台小姐姐直接把请求转给后厨引擎
        return financeDashboardService.getDashboardData(date);
    }

    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    public List<ProfitRankVO> getProfitRanking() {
        return financeDashboardService.getProfitRanking();
    }

    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    public ShiftHandoverVO getShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        return financeDashboardService.getShiftHandover(startTime, cashierName);
    }

    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    public List<CampaignReviewVO> getCampaignReview() {
        return financeDashboardService.getCampaignReview();
    }

    @Operation(summary = "6.2 支付渠道及虚拟抵扣占比分析")
    @GetMapping("/channel-mix")
    public ChannelMixAnalysisVO getChannelMixAnalysis(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeDashboardService.getChannelMixAnalysis(startDate, endDate);
    }

    @Operation(summary = "6.6 收银风控雷达看板")
    @GetMapping("/risk-control")
    public RiskControlVO getRiskControlData(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeDashboardService.getRiskControlData(startDate, endDate);
    }
}