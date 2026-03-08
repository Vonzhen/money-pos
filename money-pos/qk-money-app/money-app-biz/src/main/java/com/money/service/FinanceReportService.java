package com.money.service;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import com.money.dto.Finance.FinanceWaterfallVO;
import java.util.List;

public interface FinanceReportService {
    List<FinanceWaterfallVO> getDailyWaterfallReport(FinanceWaterfallQueryDTO queryDTO);
}