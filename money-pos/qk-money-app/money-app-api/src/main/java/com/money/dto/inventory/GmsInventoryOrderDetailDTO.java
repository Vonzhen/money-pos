package com.money.dto.inventory;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class GmsInventoryOrderDetailDTO {
    private Long goodsId;     // 扫码枪扫出的商品ID
    private Integer qty;      // 填写的进货数量
    private BigDecimal price; // 填写的进货单价
}