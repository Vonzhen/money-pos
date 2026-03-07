package com.money.dto.Pos;

import com.money.constant.GoodsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class PosGoodsVO {
    private Long id;

    @Schema(description = "条码")
    private String barcode;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Schema(description = "进价")
    private BigDecimal purchasePrice;

    @Schema(description = "售价 (系统基准零售价)")
    private BigDecimal salePrice;

    @Schema(description = "历史遗留字段: 旧会员价")
    private BigDecimal vipPrice;

    @Schema(description = "历史遗留字段: 旧用券")
    private BigDecimal coupon;

    @Schema(description = "库存")
    private Long stock;

    @Schema(description = "状态")
    private GoodsStatus status;

    @Schema(description = "是否参与满减(1:参与, 0:不参与)")
    private Integer isDiscountParticipable;

    // 🌟 新增：套餐标识牌
    @Schema(description = "是否套餐(1:是, 0:否)")
    private Integer isCombo;

    @Schema(description = "多级会员专属价")
    private Map<String, BigDecimal> levelPrices;

    @Schema(description = "多级会员专属券")
    private Map<String, BigDecimal> levelCoupons;
}