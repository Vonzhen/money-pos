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
    // 🌟 新增：支付子标签(如: WECHAT, ALIPAY)
    private String payTag;
    // 支付方式代码
    private String payMethodCode;
    // 支付金额
    private BigDecimal payAmount;
    // 创建时间
    private LocalDateTime createTime;
}