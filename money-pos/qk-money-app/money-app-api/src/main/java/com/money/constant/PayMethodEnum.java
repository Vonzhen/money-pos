package com.money.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PayMethodEnum {
    // 🌟 核心建模：AGGREGATE(聚合扫码)是独立支付方式，微信/支付宝将作为它的 tag 传入
    CASH("CASH", true, false),      // 现金：支持找零，非资产
    BALANCE("BALANCE", false, true),// 余额：不支持找零，是资产
    AGGREGATE("AGGREGATE", false, false); // 聚合扫码：不支持找零，非资产

    private final String code;
    private final boolean allowChange; // 是否允许找零
    private final boolean isAsset;     // 是否会员资产

    public static PayMethodEnum fromCode(String code) {
        for (PayMethodEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) return e;
        }
        return null; // 返回 null 代表未知/非法支付方式
    }
}