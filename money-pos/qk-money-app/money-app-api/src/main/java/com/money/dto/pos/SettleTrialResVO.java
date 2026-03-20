package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SettleTrialResVO {
    private BigDecimal totalAmount = BigDecimal.ZERO;         // 订单原价总计
    private BigDecimal finalPayAmount = BigDecimal.ZERO;      // 🌟 最终应收金额 (唯一绝对真理)
    private BigDecimal participatingAmount = BigDecimal.ZERO; // 参与满减的商品总额
    private BigDecimal costAmount = BigDecimal.ZERO;          // 成本总计 (仅后端使用，可不传前端)

    // 优惠明细
    private BigDecimal memberCouponDeduct = BigDecimal.ZERO;  // 单品会员券抵扣总计
    private BigDecimal voucherDeduct = BigDecimal.ZERO;       // 满减券抵扣总计
    private BigDecimal manualDeduct = BigDecimal.ZERO;        // 手工直减总计

    private List<ItemRes> items = new ArrayList<>();          // 算价后的明细列表

    @Data
    public static class ItemRes {
        private Long goodsId;
        private BigDecimal originalPrice; // 原单价
        private BigDecimal realPrice;     // 会员实价
        private BigDecimal couponDeduct;  // 该行扣的单品券
        private Integer quantity;
        private BigDecimal subTotal;      // 该行实收小计
        private BigDecimal costPrice;
    }
}