package com.money.service;

import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.HourlyTrafficVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO; // 🌟 导入分类VO
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.web.vo.PageVO;

import java.time.LocalDateTime;
import java.util.List;

public interface OmsSalesAnalysisService {

    SalesDashboardVO getSalesDashboard(String startDate, String endDate);

    List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension);

    List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate);

    OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime);

    PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO);

    List<HourlyTrafficVO> getTrafficAnalysis(Integer dayOfWeek);

    List<OmsSalesDataVO.TimeTrafficVO> getWeeklyTraffic();

    List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTraffic();

    // 🌟 新增：获取商品分类销售占比
    List<CategorySalesVO> getCategorySales(String startDate, String endDate);
}