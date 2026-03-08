package com.money.dto.Finance;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FinanceWaterfallQueryDTO {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}