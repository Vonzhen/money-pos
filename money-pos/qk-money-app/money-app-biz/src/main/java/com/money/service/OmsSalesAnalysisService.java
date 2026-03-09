package com.money.service;

import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO; // 🌟 修复：导入报表 VO
import java.util.List; // 🌟 修复：导入 Java 标准 List 集合

public interface OmsSalesAnalysisService {

    /**
     * 5.2 & 5.3 一键获取门店经营作战室全盘数据
     */
    SalesDashboardVO getSalesDashboard(String startDate, String endDate);

    /**
     * 5.3 经营业绩多维汇总报表 (日/周/月)
     */
    List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension);
}