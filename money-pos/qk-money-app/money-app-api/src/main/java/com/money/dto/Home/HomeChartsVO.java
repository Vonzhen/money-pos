package com.money.dto.Home;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "大屏图表聚合数据")
public class HomeChartsVO {
    @Schema(description = "近7日营业趋势")
    private List<TrendChartVO> trendData;
    @Schema(description = "品牌营收占比")
    private List<BrandPieVO> pieData;
    @Schema(description = "等级分布矩阵")
    private List<MemberBarVO> barData;
}