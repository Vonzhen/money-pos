package com.money.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OmsOrderPay {
    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;
    // 订单号
    private String orderNo;
    // 支付方式名称
    private String payMethodName;
    // 🌟 支付子标签(如: WECHAT, ALIPAY)
    private String payTag;
    // 支付方式代码
    private String payMethodCode;

    // 【老字段保留】为了向下兼容，依然存入净收金额
    private BigDecimal payAmount;

    // ==========================================
    // 🌟 P1-2 核心修复：支付记录时空胶囊 (真实快照)
    // ==========================================
    // 1. 收银员实际录入的金额 (实收)
    private BigDecimal originalAmount;
    // 2. 扣除找零后，真正进账的金额 (净入账)
    private BigDecimal netAmount;
    // 3. 找给顾客的金额 (找零分配)
    private BigDecimal changeAllocated;

    // 创建时间
    private LocalDateTime createTime;
}