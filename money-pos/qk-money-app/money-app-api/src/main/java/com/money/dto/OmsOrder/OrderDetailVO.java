package com.money.dto.OmsOrder;

import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "订单详情VO")
public class OrderDetailVO extends OmsOrderVO {

    @Schema(description = "订单明细列表")
    private List<OmsOrderDetailVO> orderDetails; // 🌟 补齐此字段，解决 setOrderDetails 报错
}