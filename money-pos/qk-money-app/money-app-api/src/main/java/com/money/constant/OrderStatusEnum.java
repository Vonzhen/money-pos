package com.money.constant;

/**
 * 🌟 金融级订单状态机枚举
 */
public enum OrderStatusEnum {

    /**
     * 待支付 (预留，当前 POS 场景通常直接 PAID)
     */
    UNPAID,

    /**
     * 已支付 (正常结算完成)
     */
    PAID,

    /**
     * 部分退款 (发生过部分退货，但还能继续退)
     */
    PARTIAL_REFUNDED,

    /**
     * 全额退款 (终态，不可再操作)
     */
    REFUNDED,

    /**
     * 已取消 (未支付直接作废的订单)
     */
    CLOSED;
}