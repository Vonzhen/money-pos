package com.money.dto.pos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 🌟 单品级计价结果明细
 */
@Data
@Schema(description = "单品计价结果快照")
public class PricingItemResult {
    private Long goodsId;
    private Integer quantity;
    private BigDecimal costPrice;          // 成本价快照
    private BigDecimal unitOriginalPrice;  // 单品零售价 (吊牌价)
    private BigDecimal unitRealPrice;      // 单品会员价 (成交底价)
    private BigDecimal subTotalRetail;     // 行小计: 零售价
    private BigDecimal subTotalMember;     // 行小计: 会员价
    private BigDecimal subTotalPrivilege;  // 行小计: 特权原值 (Retail - Member)
}