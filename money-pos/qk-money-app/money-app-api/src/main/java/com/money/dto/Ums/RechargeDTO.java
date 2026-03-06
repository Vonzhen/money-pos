package com.money.dto.Ums;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RechargeDTO {
    private Long memberId;
    private String type; // BALANCE, COUPON, VOUCHER
    private BigDecimal amount;
    private BigDecimal giftCoupon;
    private Long ruleId;
    private Integer quantity;
    private String remark;
}