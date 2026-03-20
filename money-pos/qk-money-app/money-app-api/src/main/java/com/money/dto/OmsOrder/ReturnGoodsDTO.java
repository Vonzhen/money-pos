package com.money.dto.OmsOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 🌟 部分退货请求对象
 */
@Data
@Schema(description = "部分退货请求")
public class ReturnGoodsDTO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单明细ID")
    private Long detailId;

    @Schema(description = "退货数量")
    private Integer returnQty;

    // 如果您的原始业务里还有其他字段，请告诉我
}