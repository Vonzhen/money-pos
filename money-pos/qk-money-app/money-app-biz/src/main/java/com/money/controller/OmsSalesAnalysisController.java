package com.money.controller;

import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO; // 🌟 导入营销 VO
import com.money.service.OmsSalesAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OmsSalesAnalysis", description = "5.x 订单与销售业绩大屏分析")
@RestController
@RequestMapping("/oms/analysis")
@RequiredArgsConstructor
public class OmsSalesAnalysisController {

    private final OmsSalesAnalysisService omsSalesAnalysisService;

    @Operation(summary = "5.2 & 5.3 门店经营作战室全盘数据")
    @GetMapping("/dashboard")
    public SalesDashboardVO getSalesDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getSalesDashboard(startDate, endDate);
    }

    @Operation(summary = "5.3 经营业绩多维汇总报表")
    @GetMapping("/report")
    public List<PerformanceReportVO> getPerformanceReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAILY") String dimension) {
        return omsSalesAnalysisService.getPerformanceReport(startDate, endDate, dimension);
    }

    @Operation(summary = "5.4 营销活动 ROI 深度分析")
    @GetMapping("/marketing-roi")
    public List<MarketingRoiVO> getMarketingRoiAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        // 🌟 修正：使用 omsSalesAnalysisService 而不是 gms
        return omsSalesAnalysisService.getMarketingRoiAnalysis(startDate, endDate);
    }
}