package com.money.service.impl;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import com.money.dto.Finance.FinanceWaterfallVO;
import com.money.mapper.FinanceReportMapper;
import com.money.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements FinanceReportService {

    private final FinanceReportMapper financeReportMapper;

    @Override
    public List<FinanceWaterfallVO> getDailyWaterfallReport(FinanceWaterfallQueryDTO queryDTO) {
        // 直接调用底层 Mapper 进行聚合计算
        return financeReportMapper.getDailyWaterfallReport(queryDTO.getStartTime(), queryDTO.getEndTime());
    }
}