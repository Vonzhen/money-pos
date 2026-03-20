package com.money.service;

import com.money.dto.Finance.FinanceDataVO.*;

public interface FinanceShiftService {
    ShiftHandoverVO getShiftHandover(String startTime, String cashierName);
}