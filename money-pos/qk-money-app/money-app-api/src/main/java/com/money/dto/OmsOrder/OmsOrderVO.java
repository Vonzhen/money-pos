package com.money.dto.OmsOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单表
 * </p>
 */
@Data
@Schema(description = "订单表")
public class OmsOrderVO {

    private Long id;

    @Schema(description="订单号")
    private String orderNo;

    @Schema(description="会员名")
    private String member;

    @Schema(description="会员id")
    private Long memberId;

    @Schema(description="vip单")
    private Boolean vip;

    @Schema(description="状态")
    private String status;

    @Schema(description="联系方式")
    private String contact;

    @Schema(description="省份")
    private String province;

    @Schema(description="城市")
    private String city;

    @Schema(description="地区")
    private String district;

    @Schema(description="详细地址")
    private String address;

    @Schema(description="总成本")
    private BigDecimal costAmount;

    @Schema(description="总价")
    private BigDecimal totalAmount;

    @Schema(description="实付款")
    private BigDecimal payAmount;

    @Schema(description="抵用券(旧版兼容字段)")
    private BigDecimal couponAmount;

    // ==========================================
    // 🌟 核心一统：对外输出绝对真实的“券核销”与“店铺让利”
    // ==========================================
    @Schema(description="实际核销会员券 (大一统主口径)")
    private BigDecimal actualCouponDeduct;

    @Schema(description="抹零/店铺免券让利金额")
    private BigDecimal waivedCouponAmount;

    @Schema(description="最终销售金额")
    private BigDecimal finalSalesAmount;

    @Schema(description="备注")
    private String remark;

    @Schema(description="支付时间")
    private LocalDateTime paymentTime;

    @Schema(description="完成时间")
    private LocalDateTime completionTime;

    @Schema(description="满减券抵扣金额")
    private BigDecimal useVoucherAmount;

    @Schema(description="手工整单优惠金额")
    private BigDecimal manualDiscountAmount;

    private LocalDateTime createTime;
}