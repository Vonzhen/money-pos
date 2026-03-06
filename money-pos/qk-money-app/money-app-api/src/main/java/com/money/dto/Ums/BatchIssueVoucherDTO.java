package com.money.dto.Ums;

import lombok.Data;
import java.util.List;

@Data
public class BatchIssueVoucherDTO {
    private List<Long> memberIds; // 选中的流失会员ID列表
    private Long ruleId;          // 要派发的满减券规则ID
    private Integer quantity;     // 每人派发几张
}