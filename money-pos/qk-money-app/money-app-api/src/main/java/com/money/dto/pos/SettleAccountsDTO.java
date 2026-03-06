package com.money.dto.Pos;

import com.money.dto.OmsOrderDetail.OmsOrderDetailDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SettleAccountsDTO {

    @Schema(description = "会员ID")
    private Long member;

    @Schema(description = "使用的满减券规则ID")
    private Long usedCouponRuleId;

    @Schema(description = "使用的满减券张数")
    private Integer usedCouponCount;

    @NotEmpty
    @Schema(description = "订单详情")
    private List<OmsOrderDetailDTO> orderDetail;

    @Schema(description = "是否免收会员券")
    private Boolean waiveCoupon;

    // 🌟 贯彻解耦架构：前台只管传原始输入的优惠数值，不参与计算！
    @Schema(description = "整单优惠金额")
    private BigDecimal manualDiscountAmount;

    @Schema(description = "组合支付明细流水")
    private List<PaymentItem> payments;

    @Data
    public static class PaymentItem {
        private String payMethodCode;
        private String payMethodName;
        private BigDecimal payAmount;
    }
}