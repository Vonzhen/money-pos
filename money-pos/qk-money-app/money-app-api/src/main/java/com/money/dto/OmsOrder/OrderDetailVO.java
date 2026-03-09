package com.money.dto.OmsOrder;

import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.dto.OmsOrderLog.OmsOrderLogVO;
import com.money.dto.UmsMember.UmsMemberVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 订单明细表 (含全局审计聚合)
 * </p>
 */
@Data
@Schema(description = "订单明细表")
public class OrderDetailVO {

    @Schema(description = "会员")
    private UmsMemberVO member;

    @Schema(description = "订单")
    private OmsOrderVO order;

    @Schema(description = "订单详情")
    private List<OmsOrderDetailVO> orderDetail;

    @Schema(description = "订单日志")
    private List<OmsOrderLogVO> orderLog;

    @Schema(description = "支付流水账明细")
    private List<com.money.entity.OmsOrderPay> payments;

    // 🌟 核心新增：由后端绝对掌管计算的支付渠道聚合
    @Schema(description = "余额实收聚合")
    private java.math.BigDecimal balanceAmount;

    @Schema(description = "聚合扫码实收聚合")
    private java.math.BigDecimal scanAmount;

    @Schema(description = "现金实收聚合")
    private java.math.BigDecimal cashAmount;
}