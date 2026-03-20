package com.money.dto.UmsMember;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberRankVO {
    private Long id;
    private String name;
    private String phone;
    private BigDecimal amount; // 用于接收金额(消费额或余额)
    private Integer times;     // 用于接收次数
}