package com.money.dto.GmsGoods;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GmsGoodsExcelDTO {
    @ExcelProperty("商品条码")
    private String barcode;

    @ExcelProperty("商品名称")
    private String name;

    @ExcelProperty("进货价")
    private BigDecimal purchasePrice;

    @ExcelProperty("零售价")
    private BigDecimal salePrice;

    // 🔥 彻底统一命名：普通会员价
    @ExcelProperty("普通会员价")
    private BigDecimal vipPrice;

    @ExcelProperty("黄金会员价")
    private BigDecimal goldPrice;

    @ExcelProperty("铂金会员价")
    private BigDecimal platinumPrice;

    @ExcelProperty("内部会员价")
    private BigDecimal internalPrice;

    @ExcelProperty("当前库存")
    private Integer stock;
}