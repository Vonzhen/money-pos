package com.money.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 🌟 金融级订单状态机枚举 (已剔除 RETURN 和 DONE)
 */
public enum OrderStatusEnum {

    /** 待支付 */
    UNPAID,

    /** 已支付 (正常结算完成) */
    PAID,

    /** 部分退款 (发生过部分退货，但还能继续退) */
    PARTIAL_REFUNDED,

    /** 全额退款 (终态，不可再操作) */
    REFUNDED,

    /** 已取消 (未支付直接作废的订单) */
    CLOSED;

    /**
     * 🌟 核心引擎：定义“经营有效集”
     * 只要算营业额、算利润、画走势图，统统只查这三个状态！
     */
    public static List<String> getValidFinancialStatus() {
        return Arrays.asList(PAID.name(), PARTIAL_REFUNDED.name(), REFUNDED.name());
    }
}