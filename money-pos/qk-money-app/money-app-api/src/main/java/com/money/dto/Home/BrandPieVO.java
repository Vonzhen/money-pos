package com.money.dto.Home;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "品牌营收贡献图表数据")
public class BrandPieVO {
    @Schema(description = "品牌名称")
    private String name;
    @Schema(description = "营收金额")
    private BigDecimal value;
}