package com.money.dto.OmsOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "真实损益毛利审计快照")
public class ProfitAuditVO {

    @Schema(description = "关联单号")
    private String orderNo;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "销售原价 (吊牌价)")
    private BigDecimal salePrice;

    @Schema(description = "实际成交单价 (均摊各项优惠后)")
    private BigDecimal goodsPrice;

    @Schema(description = "交易时刻进价快照 (成本)")
    private BigDecimal purchasePrice;

    @Schema(description = "单件净利 (实际成交价 - 进价)")
    private BigDecimal unitProfit;

    @Schema(description = "毛利率 (单件净利 / 实际成交价)")
    private BigDecimal profitMargin;

    @Schema(description = "异常探针：是否缺失成本快照 (1:缺失, 0:正常)")
    private Integer isMissingCost;

    @Schema(description = "异常探针：是否负毛利倒挂 (1:倒挂, 0:正常)")
    private Integer isNegativeMargin;

    @Schema(description = "交易时间")
    private java.time.LocalDateTime createTime;
}