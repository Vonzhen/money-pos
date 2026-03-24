package com.money.controller;

import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO; // 🌟 导入内部类 VO
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

    @GetMapping("/dashboard")
    @Operation(summary = "获取门店经营作战室核心大盘数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public SalesDashboardVO getSalesDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getSalesDashboard(startDate, endDate);
    }

    @GetMapping("/report")
    @Operation(summary = "按日/周/月拉取业绩汇总列表")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<PerformanceReportVO> getPerformanceReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAILY") String dimension) {
        return omsSalesAnalysisService.getPerformanceReport(startDate, endDate, dimension);
    }

    @GetMapping("/marketing-roi")
    @Operation(summary = "获取营销活动投入产出比数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<MarketingRoiVO> getMarketingRoiAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getMarketingRoiAnalysis(startDate, endDate);
    }

    @GetMapping("/audit-page")
    @Operation(summary = "获取利润异常审计分页数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsSalesAnalysisService.getProfitAuditPage(queryDTO);
    }

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

    // ==========================================
    // 🌟 新增：5. 商品分类销售占比分析
    // ==========================================
    @GetMapping("/category-sales")
    @Operation(summary = "获取商品分类销售占比")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<CategorySalesVO> getCategorySales(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getCategorySales(startDate, endDate);
    }
}