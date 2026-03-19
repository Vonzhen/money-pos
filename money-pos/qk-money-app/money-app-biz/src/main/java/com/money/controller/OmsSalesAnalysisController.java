package com.money.controller;

import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.service.OmsSalesAnalysisService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OmsAnalysis", description = "订单大盘与客流分析")
@RestController
@RequestMapping("/oms/analysis")
@RequiredArgsConstructor
public class OmsSalesAnalysisController {

    private final OmsSalesAnalysisService omsSalesAnalysisService;

    // ==========================================
    // 🌟 新增合入：1. 经营作战室大盘 (修复 dashboard 404)
    // ==========================================
    @GetMapping("/dashboard")
    @Operation(summary = "获取门店经营作战室核心大盘数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public SalesDashboardVO getSalesDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getSalesDashboard(startDate, endDate);
    }

    // ==========================================
    // 🌟 新增合入：2. 经营业绩汇总报表 (修复 report 404)
    // ==========================================
    @GetMapping("/report")
    @Operation(summary = "按日/周/月拉取业绩汇总列表")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<PerformanceReportVO> getPerformanceReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAILY") String dimension) {
        return omsSalesAnalysisService.getPerformanceReport(startDate, endDate, dimension);
    }

    // ==========================================
    // 🌟 新增合入：3. 营销活动核销与ROI复盘 (修复 marketing-roi 404)
    // ==========================================
    @GetMapping("/marketing-roi")
    @Operation(summary = "获取营销活动投入产出比数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<MarketingRoiVO> getMarketingRoiAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getMarketingRoiAnalysis(startDate, endDate);
    }

    // ==========================================
    // 🌟 新增合入：4. 利润风控审计 (以防后续审计页面 404)
    // ==========================================
    @GetMapping("/audit-page")
    @Operation(summary = "获取利润异常审计分页数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsSalesAnalysisService.getProfitAuditPage(queryDTO);
    }

    // ==========================================
    // 🌟 保留原有：8.2 客流分析接口 (严格保持原有 URL 路径)
    // ==========================================
    @Operation(summary = "8.2 获取客流价值分析(办事罗盘)")
    @GetMapping("/traffic")
    public List<OmsSalesDataVO.HourlyTrafficVO> getTrafficAnalysis(
            @RequestParam(required = false) Integer dayOfWeek) {
        return omsSalesAnalysisService.getTrafficAnalysis(dayOfWeek);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "按周宏观大盘(周一至周日)")
    @org.springframework.web.bind.annotation.GetMapping("/weekly-traffic")
    public List<OmsSalesDataVO.TimeTrafficVO> getWeeklyTraffic() {
        return omsSalesAnalysisService.getWeeklyTraffic();
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "按月宏观潮汐(1号至31号)")
    @org.springframework.web.bind.annotation.GetMapping("/monthly-traffic")
    public List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTraffic() {
        return omsSalesAnalysisService.getMonthlyTraffic();
    }
}