package com.money.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("oms_refund_idempotent")
public class OmsRefundIdempotent {
    private String reqId;
    private String bizType;
    private Long tenantId; // 🌟 满足多租户插件要求
    private LocalDateTime createTime;
}