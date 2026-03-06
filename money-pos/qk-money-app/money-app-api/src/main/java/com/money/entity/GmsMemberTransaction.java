package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GmsMemberTransaction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long memberId;      // 会员ID
    private String type;        // 交易类型 (RECHARGE, CONSUME, REFUND, IMPORT)
    private BigDecimal amount;  // 变动金额
    private BigDecimal balanceAfter; // 变动后余额 (极其重要，对账快照)
    private String orderNo;     // 关联单号
    private String remark;      // 备注
    private LocalDateTime createTime;
    private Long tenantId;
}