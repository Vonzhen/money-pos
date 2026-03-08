package com.money.dto.UmsMember;

import com.money.web.dto.PageQueryRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "会员资金流水查询条件")
public class UmsMemberLogQueryDTO extends PageQueryRequest {

    @Schema(description = "会员ID")
    private Long memberId;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "资金类型: BALANCE(本金), COUPON(券)")
    private String type;

    @Schema(description = "操作类型: RECHARGE, CONSUME, IMPORT等")
    private String operateType;

    @Override
    public Map<String, String> sortKeyMap() {
        // 资金流水默认按时间倒序，不需要特殊的排序字段映射，返回空即可防止报错
        return Collections.emptyMap();

    }
}