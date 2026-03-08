package com.money.dto.UmsMember;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UmsMemberLogVO {
    private Long id;
    private String memberName; // 会员名字
    private String memberPhone; // 会员手机
    private String type;       // BALANCE / COUPON
    private String operateType; // RECHARGE / CONSUME / IMPORT / GIFT
    private BigDecimal amount;  // 变动金额
    private BigDecimal afterAmount; // 变动后余额
    private String remark;      // 备注
    private LocalDateTime createTime;
    private String orderNo;
    private BigDecimal realAmount;
}