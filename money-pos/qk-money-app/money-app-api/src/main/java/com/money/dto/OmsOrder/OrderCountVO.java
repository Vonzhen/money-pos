package com.money.dto.OmsOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 🌟 订单统计全能版VO
 * 适配：首页大盘 (HomeService)、订单审计 (OmsOrderService)
 */
@Data
@Schema(description = "订单统计VO")
public class OrderCountVO {

    @Schema(description = "订单总数")
    private Long orderCount;

    @Schema(description = "销售总额 (适配审计接口)")
    private BigDecimal totalSales;

    @Schema(description = "预估利润")
    private BigDecimal profit;

    // --- 🌟 补齐以下字段，适配 HomeServiceImpl (首页大盘) ---

    @Schema(description = "累计销售额 (适配首页大盘)")
    private BigDecimal saleCount;

    @Schema(description = "累计成本额 (适配首页大盘)")
    private BigDecimal costCount;
}