package com.money.dto.pos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 🌟 全局唯一计价真理对象 (前后端统一的财务契约)
 */
@Data
@Schema(description = "计价引擎真理结果")
public class PricingResult {

    @Schema(description = "【价格轨】零售总额 (原价合计)")
    private BigDecimal retailAmount = BigDecimal.ZERO;

    @Schema(description = "【价格轨】会员基准总额 (底价合计)")
    private BigDecimal memberAmount = BigDecimal.ZERO;

    @Schema(description = "【特权层】会员特权原值 (retailAmount - memberAmount)")
    private BigDecimal privilegeAmount = BigDecimal.ZERO;

    @Schema(description = "【核销轨】真实扣除会员券金额 (退款/扣减资产的唯一依据)")
    private BigDecimal actualCouponDeduct = BigDecimal.ZERO;

    @Schema(description = "【核销轨】店铺免收承担额 (纯营销折让，不扣会员钱)")
    private BigDecimal waivedCouponAmount = BigDecimal.ZERO;

    @Schema(description = "【折让轨】满减券抵扣额")
    private BigDecimal voucherDeduct = BigDecimal.ZERO;

    @Schema(description = "【折让轨】手工直减额")
    private BigDecimal manualDeduct = BigDecimal.ZERO;

    @Schema(description = "【最终轨】最终应收/实付金额")
    private BigDecimal finalPayAmount = BigDecimal.ZERO;

    @Schema(description = "满减活动参与总额 (内部计算凭证)")
    private BigDecimal participatingAmount = BigDecimal.ZERO;

    @Schema(description = "本单总成本快照")
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Schema(description = "明细快照列表")
    private List<PricingItemResult> items = new ArrayList<>();
}