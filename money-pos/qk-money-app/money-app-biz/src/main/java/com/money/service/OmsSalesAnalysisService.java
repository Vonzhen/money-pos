package com.money.service;

import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.HourlyTrafficVO;
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

    // ==========================================
    // 🌟 8.2 核心升级：增加 dayOfWeek (星期几) 筛选维度
    // ==========================================
    List<HourlyTrafficVO> getTrafficAnalysis(Integer dayOfWeek);

    List<OmsSalesDataVO.TimeTrafficVO> getWeeklyTraffic();
    List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTraffic();
}