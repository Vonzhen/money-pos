package com.money.dto.Pos;

import com.money.constant.GoodsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : money
 * @version : 1.0.0
 * @description : pos商品VO
 * @createTime : 2022-04-14 22:06:10
 */
@Data
public class PosGoodsVO {

    private Long id;

    /**
     * 条码
     */
    private String barcode;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 进价
     */
    private BigDecimal purchasePrice;

    /**
     * 售价
     */
    private BigDecimal salePrice;

    /**
     * 会员价
     */
    private BigDecimal vipPrice;

    /**
     * 用券
     */
    private BigDecimal coupon;

    /**
     * 库存
     */
    private Long stock;

    /**
     * 状态
     */
    private GoodsStatus status;

    @Schema(description = "多级会员专属价")
    private java.util.Map<String, java.math.BigDecimal> levelPrices;
}
