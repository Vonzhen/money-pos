package com.money.dto.GmsGoods;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class TurnoverDataVO {

    // 整个看板的返回体
    @Data
    public static class TurnoverDashboardVO {
        private List<WarningItemVO> replenishList; // 急需补货清单
        private List<WarningItemVO> deadStockList; // 积压库存清单
    }

    // 每一条预警商品的数据结构
    @Data
    public static class WarningItemVO {
        private Long goodsId;
        private String goodsName;
        private Integer currentStock;       // 当前实际库存
        private Integer sales30Days;        // 过去30天销量
        private Integer sales90Days;        // 过去90天销量
        private Integer suggestedQty;       // 算法建议补货量
        private String warningType;         // 预警类型：REPLENISH(补货), DEAD_STOCK(僵尸货)
        private Integer deadDays;           // 距离上次售出的天数
        private String lastSaleTime;        // 最后售出时间
    }
}