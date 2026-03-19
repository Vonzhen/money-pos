package com.money.dto.OmsOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "财务毛利审计视图对象")
public class ProfitAuditVO {

    @Schema(description = "关联单号")
    private String orderNo;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "交易时间")
    private LocalDateTime createTime;

    @Schema(description = "吊牌价")
    private BigDecimal salePrice;

    @Schema(description = "实际成交价")
    private BigDecimal goodsPrice;

    @Schema(description = "进价快照")
    private BigDecimal purchasePrice;

    @Schema(description = "单件净利")
    private BigDecimal unitProfit;

    @Schema(description = "毛利率")
    private BigDecimal profitMargin;

    @Schema(description = "是否缺失成本 (1:是 0:否)")
    private Integer isMissingCost;
}