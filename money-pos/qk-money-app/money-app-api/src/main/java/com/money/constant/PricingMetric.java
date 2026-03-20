package com.money.constant;

/**
 * 🌟 计价标准法典 (统一全系统财务口径，防止语义漂移)
 */
public class PricingMetric {

    public static final String RETAIL_AMOUNT = "零售价合计：商品无会员身份时的吊牌价合计。";

    public static final String MEMBER_AMOUNT = "会员价合计：识别会员后，锁定的成交底价合计。也是最终应付的计算基座。";

    public static final String PRIVILEGE_AMOUNT = "特权原值：零售价 - 会员价。代表会员身份带来的理论省钱额。";

    public static final String ACTUAL_COUPON_DEDUCT = "真实扣券额：免收关闭时 = 特权原值；免收开启时 = 0。用于退款和扣除会员资产。";

    public static final String WAIVED_COUPON_AMOUNT = "店铺承担额：免收开启时 = 特权原值；否则 = 0。代表店铺的营销让利成本。";

    public static final String FINAL_PAY_AMOUNT = "最终应收：会员价合计 - 满减券 - 手工优惠。";
}