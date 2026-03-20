package com.money.dto.OmsOrder;

import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.dto.UmsMember.UmsMemberVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单详情VO (大一统解耦版)")
public class OrderDetailVO extends OmsOrderVO {

    @Schema(description = "订单明细列表")
    private List<OmsOrderDetailVO> orderDetails;

    // ==========================================
    // 🌟 资金对账与审计强化的扩展字段
    // ==========================================

    @Schema(description = "会员余额实付金额")
    private BigDecimal balanceAmount;

    @Schema(description = "聚合扫码实付金额")
    private BigDecimal scanAmount;

    @Schema(description = "现金实付金额")
    private BigDecimal cashAmount;

    @Schema(description = "找零金额")
    private BigDecimal changeAmount;

    @Schema(description = "关联的会员完整档案")
    private UmsMemberVO memberInfo;

    // 🌟 核心解耦：摒弃 Object 和 Entity，使用专属 VO 契约
    @Schema(description = "系统操作审计日志")
    private List<OrderLogVO> orderLog;

    @Schema(description = "底层支付流水快照")
    private List<OrderPayVO> payments;

    // ==========================================
    // 🛡️ 内部解耦视图类 (防数据库结构泄露)
    // ==========================================

    @Data
    @Schema(description = "订单操作日志 (脱水版)")
    public static class OrderLogVO {
        private Long id;
        private String description;
        private String createBy;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "订单支付流水 (脱水版)")
    public static class OrderPayVO {
        private Long id;
        private String payMethodName;
        private String payTag;
        private String payMethodCode;
        private BigDecimal payAmount;
        private LocalDateTime createTime;
    }
}