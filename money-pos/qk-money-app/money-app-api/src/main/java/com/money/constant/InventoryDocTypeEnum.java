package com.money.constant;

public enum InventoryDocTypeEnum {
    INBOUND("采购入库"),
    OUTBOUND("报损出库"),
    CHECK("盘点对冲"),
    SALE_OUT("销售出库");

    private final String desc;

    InventoryDocTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}