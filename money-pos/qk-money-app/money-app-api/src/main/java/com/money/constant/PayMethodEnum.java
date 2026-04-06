package com.money.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 🌟 系统底层物理资金通道 (不涉及表面中文展示)
 * 注意：不要在这里随意添加“抖音支付”、“微信支付”等逻辑标签，
 * 逻辑标签请前往数据库 `paySubTag` 字典进行纯配置化增加！
 */
@Getter
@AllArgsConstructor
public enum PayMethodEnum {
    // 物理通道 1：需要打开钱箱，允许找零，不扣除会员线上资产
    CASH("CASH", true, false),

    // 物理通道 2：扣除线上数字资产，不允许找零
    BALANCE("BALANCE", false, true),

    // 物理通道 3：外部第三方支付聚合通道，不允许找零，具体子通道由 payTag 决定
    AGGREGATE("AGGREGATE", false, false);

    private final String code;
    private final boolean allowChange; // 是否允许找零
    private final boolean isAsset;     // 是否会员资产

    public static PayMethodEnum fromCode(String code) {
        for (PayMethodEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) return e;
        }
        return null; // 返回 null 代表这是非法的/未授权的物理支付方式
    }
}