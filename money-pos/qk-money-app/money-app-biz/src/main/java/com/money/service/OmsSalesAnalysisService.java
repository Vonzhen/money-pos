package com.money.service;

import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.web.vo.PageVO;

import java.time.LocalDateTime;
import java.util.List;

public interface OmsSalesAnalysisService {

    /**
     * 5.2 & 5.3 门店经营作战室全盘数据
     */
    SalesDashboardVO getSalesDashboard(String startDate, String endDate);

    /**
     * 5.3 经营业绩多维汇总报表 (日/周/月)
     */
    List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension);

    /**
     * 5.4 营销活动核销成本与 ROI 深度分析
     */
    List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate);

    /**
     * 大盘订单与基础销售额统计
     */
    OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 利润审计分页列表
     */
    PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO);
}