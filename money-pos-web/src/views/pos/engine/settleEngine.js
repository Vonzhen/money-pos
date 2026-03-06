/**
 * Money-POS 结算引擎 V4.6.1
 * 专门处理价格、优惠、分摊等纯数学计算
 * 已同步字典暗号：HJ_VIP, BJ_VIP, INNER
 */
export const SettleEngine = {
    // 1. 计算商品的真实单价 (考虑会员等级价)
    getRealPrice(item, member) {
        if (!member?.id) return item.salePrice || 0;

        // 获取当前会员的真实等级 ID (例如 HJ_VIP)
        const memberLevel = member.levelId || member.type;

        // 🌟 核心匹配：如果该商品有设置对应的等级价，则优先使用
        if (memberLevel && item.levelPrices && item.levelPrices[memberLevel] !== undefined) {
            return item.levelPrices[memberLevel];
        }

        // 否则回退：通用会员价 -> 零售价
        return item.vipPrice || item.salePrice || 0;
    },

    // 2. 计算本单需要扣减的会员券 (抵扣券) 总额
    calculateTotalCouponNeeded(cartList, member) {
        if (!member?.id) return 0;
        return cartList.reduce((sum, item) => {
            const realPrice = this.getRealPrice(item, member);
            const diff = (item.salePrice || 0) - realPrice;
            return sum + (diff > 0 ? diff * item.quantity : 0);
        }, 0);
    },

    // 3. 计算最终应付总额 (扣除满减券和整单优惠后)
    calculateFinalPayAmount(totalAmount, selectedCouponRule, usedCouponCount, manualDiscount) {
        // 后端使用的是 discountAmount 字段
        const couponDeduction = selectedCouponRule ? (usedCouponCount * (selectedCouponRule.discountAmount || selectedCouponRule.deduction || 0)) : 0;
        return Math.max(0, totalAmount - couponDeduction - (manualDiscount || 0));
    }
};