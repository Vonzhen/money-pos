package com.money.controller;

import com.money.dto.Finance.FinanceDataVO;
import com.money.service.FinanceDashboardService;
import com.money.service.FinanceProfitService;
import com.money.service.FinanceShiftService;
import com.money.service.FinanceRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final FinanceDashboardService financeDashboardService;
    private final FinanceProfitService financeProfitService;
    private final FinanceShiftService financeShiftService;
    private final FinanceRiskService financeRiskService;

    @Operation(summary = "6.1 财务瀑布流全口径日结大屏")
    @GetMapping("/dashboard")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.FinanceDashboardVO getDashboardData(@RequestParam(required = false) String date) {
        return financeDashboardService.getDashboardData(date);
    }

    @Operation(summary = "6.2 支付渠道及虚拟抵扣占比分析")
    @GetMapping("/channel-mix")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.ChannelMixAnalysisVO getChannelMixAnalysis(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeDashboardService.getChannelMixAnalysis(startDate, endDate);
    }

    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<FinanceDataVO.ProfitRankVO> getProfitRanking() {
        return financeProfitService.getProfitRanking();
    }

    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<FinanceDataVO.CampaignReviewVO> getCampaignReview() {
        return financeProfitService.getCampaignReview();
    }

    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.ShiftHandoverVO getShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        return financeShiftService.getShiftHandover(startTime, cashierName);
    }

    @Operation(summary = "6.6 收银风控雷达看板")
    @GetMapping("/risk-control")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public Object getRiskControlData(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        // 🌟 关键点：调用的是 service 的新方法 getRiskSummary
        return financeRiskService.getRiskSummary(startDate, endDate);
    }

    @Operation(summary = "8.1 首页资产驾驶舱核心数据")
    @GetMapping("/dashboard/asset")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.AssetDashboardVO getAssetDashboard() {
        return financeDashboardService.getAssetDashboard();
    }
}