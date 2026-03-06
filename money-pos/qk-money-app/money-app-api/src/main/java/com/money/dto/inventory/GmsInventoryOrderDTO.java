package com.money.dto.inventory;

import lombok.Data;
import java.util.List;

@Data
public class GmsInventoryOrderDTO {
    private String type;      // 单子类型：INBOUND (入库)
    private String remark;    // 备注 (比如：中秋节旺旺大礼包进货)
    private List<GmsInventoryOrderDetailDTO> details; // 包含的一大堆商品
}