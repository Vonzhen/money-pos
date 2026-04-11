package com.money.dto.OmsOrder;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "部分退货请求")
public class ReturnGoodsDTO {
    @NotBlank(message = "订单号不能为空")
    @JsonProperty("orderNo")
    @JSONField(name = "orderNo")
    private String orderNo;

    @NotNull(message = "订单明细ID不能为空")
    @JsonProperty("detailId")
    @JSONField(name = "detailId")
    private Long detailId;

    @NotNull(message = "退货数量不能为空")
    @Min(value = 1, message = "退货数量至少为1")
    @JsonProperty("returnQty")
    @JSONField(name = "returnQty")
    private Integer returnQty;

    @NotBlank(message = "缺少防重放标识 reqId")
    @JsonProperty("reqId")
    @JSONField(name = "reqId")
    private String reqId;
}