package com.money.dto.GmsGoods;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class StockInRequestDTO {
    private String remark; // 入库备注
    private List<StockInItemDTO> items; // 商品明细列表

    @Data
    public static class StockInItemDTO {
        private Long goodsId;
        private Integer quantity; // 本次入库数量
        private BigDecimal purchasePrice; // 本次进价（手工录入）
    }
}