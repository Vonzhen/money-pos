package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SettleTrialReqDTO {
    private Long member;                      // 会员ID (可为空)
    private Long usedCouponRuleId;            // 使用的满减券规则ID
    private Integer usedCouponCount;          // 使用的满减券张数
    private Boolean waiveCoupon;              // 是否免收单品会员券
    private BigDecimal manualDiscountAmount;  // 手工整单直减金额
    private List<TrialItem> items;            // 购物车明细

    @Data
    public static class TrialItem {
        private Long goodsId;
        private Integer quantity;
    }
}