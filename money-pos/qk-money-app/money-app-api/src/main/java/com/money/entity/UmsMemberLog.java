package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ums_member_log")
public class UmsMemberLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String type; // BALANCE, COUPON
    private String operateType; // RECHARGE, CONSUME, GIFT
    private BigDecimal amount;
    private BigDecimal afterAmount;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String tenantId;
    /**
     * 关联订单号(消费/退款等业务)
     */
    private String orderNo;
    // ==========================================
    // 🌟 核心新增：充值时实际收到的现金（用于核算发券成本）
    // ==========================================
    private BigDecimal realAmount;
}