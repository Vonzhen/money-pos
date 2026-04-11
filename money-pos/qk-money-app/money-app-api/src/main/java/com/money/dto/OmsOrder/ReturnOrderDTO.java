package com.money.dto.OmsOrder;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "整单退款请求")
public class ReturnOrderDTO {
    @NotBlank(message = "退款单号不能为空")
    @JsonProperty("orderNo")
    @JSONField(name = "orderNo")
    private String orderNo;

    @NotBlank(message = "缺少防重放标识 reqId")
    @JsonProperty("reqId")
    @JSONField(name = "reqId")
    private String reqId;
}