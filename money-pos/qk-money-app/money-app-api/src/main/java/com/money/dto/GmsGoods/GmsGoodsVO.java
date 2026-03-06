package com.money.dto.GmsGoods;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>
 * 商品表
 * </p>
 */
@Data
@Schema(description = "商品表")
public class GmsGoodsVO {

    private Long id;

    @Schema(description="品牌id")
    private Long brandId;

    @Schema(description="分类id")
    private Long categoryId;

    @Schema(description="标签")
    private String label;

    @Schema(description="条码")
    private String barcode;

    @Schema(description="商品名称")
    private String name;

    @Schema(description="商品拼音")
    private String pinyin;

    @Schema(description="商品图片")
    private String pic;

    @Schema(description="单位")
    private String unit;

    @Schema(description="规格")
    private String size;

    @Schema(description="描述")
    private String description;

    @Schema(description="进价")
    private BigDecimal purchasePrice;

    @Schema(description="售价(零售价)")
    private BigDecimal salePrice;

    @Schema(description="历史遗留字段: 旧会员价")
    private BigDecimal vipPrice;

    @Schema(description="历史遗留字段: 旧用券")
    private BigDecimal coupon;

    @Schema(description="【全新架构】等级价矩阵")
    private Map<String, BigDecimal> levelPrices;

    @Schema(description="【全新架构】等级专属券矩阵")
    private Map<String, BigDecimal> levelCoupons;

    @Schema(description="库存")
    private Long stock;

    @Schema(description="销量")
    private Long sales;

    @Schema(description="状态")
    private String status;

    @Schema(description="创建时间")
    private LocalDateTime createTime;

    @Schema(description="修改时间")
    private LocalDateTime updateTime;

    @Schema(description="是否参与满减(1-参与 0-不参与)")
    private Integer isDiscountParticipable;

    @io.swagger.v3.oas.annotations.media.Schema(description = "套餐包含的商品描述文字(表格用)")
    private String comboDesc;

    @io.swagger.v3.oas.annotations.media.Schema(description = "套餐包含的单品明细数组(表单用)")
    private java.util.List<com.money.dto.GmsGoods.GmsGoodsComboDTO> subGoodsList;
}