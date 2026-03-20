package com.money.dto.pos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "结算成功后的完整回执(完美支撑前端打印小票)")
public class SettleResultVO {
    private String orderNo;
    private BigDecimal totalAmount;      // 原价总计
    private BigDecimal finalPayAmount;   // 最终应收
    private BigDecimal totalPaid;        // 顾客实付总额(含找零部分)
    private BigDecimal changeAmount;     // 找零金额
    private BigDecimal netReceived;      // 财务净入账
    private LocalDateTime paymentTime;   // 支付时间

    // 🌟 补上漏掉的这行会员名称字段
    private String memberName;

    private BigDecimal couponDeduct;     // 会员券抵扣
    private BigDecimal voucherDeduct;    // 满减券抵扣
    private BigDecimal manualDeduct;     // 手工优惠
}