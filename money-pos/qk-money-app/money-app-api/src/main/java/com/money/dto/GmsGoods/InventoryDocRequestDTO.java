package com.money.dto.GmsGoods;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class InventoryDocRequestDTO {
    /** 单据类型：INBOUND, CHECK, OUTBOUND */
    private String type;
    /** 备注 */
    private String remark;
    /** 明细列表 */
    private List<ItemDTO> details;

    @Data
    public static class ItemDTO {
        private Long goodsId;
        /** * INBOUND: 进货数
         * OUTBOUND: 报损数
         * CHECK: 盘点后的货架【实际数量】
         */
        private Integer qty;
        /** * INBOUND: 手动录入的进价
         * OUTBOUND/CHECK: 前端不传，后端自动取系统均价
         */
        private BigDecimal price;
    }
}