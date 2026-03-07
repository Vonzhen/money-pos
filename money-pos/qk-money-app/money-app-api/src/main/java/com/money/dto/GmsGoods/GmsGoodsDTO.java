package com.money.dto.GmsGoods;

import com.money.web.dto.ValidGroup;
import com.money.constant.GoodsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Schema(description = "商品表")
public class GmsGoodsDTO {

    @NotNull(groups = ValidGroup.Update.class)
    private Long id;

    @Schema(description = "品牌id")
    private Long brandId;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "条码")
    private String barcode;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "规格")
    private String size;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "进价")
    @NotNull(groups = {ValidGroup.Save.class})
    @DecimalMin(value = "0", groups = {ValidGroup.Save.class, ValidGroup.Update.class})
    private BigDecimal purchasePrice;

    @Schema(description = "售价 (零售价)")
    @NotNull(groups = {ValidGroup.Save.class})
    @DecimalMin(value = "0", groups = {ValidGroup.Save.class, ValidGroup.Update.class})
    private BigDecimal salePrice;

    @Schema(description = "历史遗留字段: 旧会员价")
    @DecimalMin(value = "0", groups = {ValidGroup.Save.class, ValidGroup.Update.class})
    private BigDecimal vipPrice;

    @Schema(description = "历史遗留字段: 旧用券")
    @DecimalMin(value = "0", groups = {ValidGroup.Save.class, ValidGroup.Update.class})
    private BigDecimal coupon = BigDecimal.ZERO;

    @Schema(description="【全新架构】等级价矩阵")
    private Map<String, BigDecimal> levelPrices;

    @Schema(description="【全新架构】等级专属券矩阵")
    private Map<String, BigDecimal> levelCoupons;

    @Schema(description = "库存")
    @Min(value = 0, groups = {ValidGroup.Save.class, ValidGroup.Update.class})
    private Long stock;

    @Schema(description = "状态")
    private GoodsStatus status = GoodsStatus.SALE;

    @Schema(description = "是否参与满减(1:参与, 0:不参与)")
    private Integer isDiscountParticipable;

    @Schema(description = "是否为套餐(0否 1是)")
    private Integer isCombo;

    @Schema(description = "套餐包含的子商品列表")
    private java.util.List<GmsGoodsComboDTO> subGoodsList;
}