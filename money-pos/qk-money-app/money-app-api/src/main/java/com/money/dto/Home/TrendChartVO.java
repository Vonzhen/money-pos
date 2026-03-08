package com.money.dto.Home;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "营业趋势图表数据")
public class TrendChartVO {
    @Schema(description = "日期 (如 03-08)")
    private String date;
    @Schema(description = "销售额")
    private BigDecimal sales;
    @Schema(description = "净利润")
    private BigDecimal profit;
}