import { ref, computed } from 'vue';
import { req } from "@/api/index.js";

// 🌟 全局单例状态 (Global State)
const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

export function usePosStore() {

    // ==========================================
    // ⚙️ 核心引擎 1：单品价格解析器
    // ==========================================
    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        let unitPrice = Number(item.salePrice) || 0;
        let unitCoupon = 0;

        if (levelCode) {
            if (item.levelPrices && item.levelPrices[levelCode] !== undefined) {
                unitPrice = Number(item.levelPrices[levelCode]);
            }
            if (item.levelCoupons && item.levelCoupons[levelCode] !== undefined) {
                unitCoupon = Number(item.levelCoupons[levelCode]);
            }
        }

        // 记录理论应扣券值（无视免收开关，用于验证余额是否充足）
        const theoreticalCoupon = unitCoupon;

        if (isWaiveCoupon.value) {
            unitCoupon = 0;
        }

        return { unitPrice, unitCoupon, theoreticalCoupon };
    };

    // ==========================================
    // ⚙️ 核心引擎 2：购物车聚合计算 (全链路脱水防御)
    // ==========================================
    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 1), 0));

    const totalAmount = computed(() => {
        const sum = cartList.value.reduce((acc, item) => {
            const { unitPrice } = getCartItemPrices(item, currentMember.value);
            return acc + (unitPrice * (Number(item.qty) || 1));
        }, 0);
        return Number(sum.toFixed(2));
    });

    const actualCouponUsed = computed(() => {
        if (isWaiveCoupon.value) return 0;
        const sum = cartList.value.reduce((acc, item) => {
            const { unitCoupon } = getCartItemPrices(item, currentMember.value);
            return acc + (unitCoupon * (Number(item.qty) || 1));
        }, 0);
        return Number(sum.toFixed(2));
    });

    // 🌟 统一收编：理论扣券总额 (供 CheckoutModal 验证余额使用)
    const theoreticalCouponUsed = computed(() => {
        const sum = cartList.value.reduce((acc, item) => {
            const { theoreticalCoupon } = getCartItemPrices(item, currentMember.value);
            return acc + (theoreticalCoupon * (Number(item.qty) || 1));
        }, 0);
        return Number(sum.toFixed(2));
    });

    const totalCouponNeeded = computed(() => actualCouponUsed.value);

    // 🌟 统一收编：参与满减活动的商品总额 (供 BottomConsole 和 客显 使用)
    const participatingAmount = computed(() => {
        const sum = cartList.value.reduce((acc, item) => {
            if (item.isDiscountParticipable === 1) {
                const { unitPrice } = getCartItemPrices(item, currentMember.value);
                return acc + (unitPrice * (Number(item.qty) || 1));
            }
            return acc;
        }, 0);
        return Number(sum.toFixed(2));
    });

    const finalPayAmount = computed(() => {
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        const manualDeduct = Number(manualDiscount.value) || 0;
        let final = totalAmount.value - voucherDeduct - manualDeduct;
        return final > 0 ? Number(final.toFixed(2)) : 0;
    });

    // ==========================================
    // ⚙️ 核心引擎 3：支付闭环与找零核算 (四大金刚)
    // ==========================================
    const paymentStats = computed(() => {
        const targetPay = finalPayAmount.value > 0 ? finalPayAmount.value : totalAmount.value;
        const payments = paymentList.value || [];

        const aggPay = payments.find(p => p.code && p.code.includes('AGGREGATE'));
        const aggregate = Number((aggPay ? (Number(aggPay.amount) || 0) : 0).toFixed(2));

        const tendered = Number(payments.reduce((sum, p) => {
            if (p.code && p.code.includes('AGGREGATE')) return sum;
            return sum + (Number(p.amount) || 0);
        }, 0).toFixed(2));

        const totalInputs = Number((tendered + aggregate).toFixed(2));
        const change = totalInputs > targetPay ? Number((totalInputs - targetPay).toFixed(2)) : 0;
        const unpaid = Math.max(0, Number((targetPay - totalInputs).toFixed(2)));

        return { targetPay, tendered, aggregate, change, totalInputs, unpaid };
    });

    // ==========================================
    // 🛒 动作指令 (Actions)
    // ==========================================
    const addToCart = (goods) => {
        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) { exist.qty = (exist.qty || 1) + 1; }
        else { cartList.value.unshift({ ...goods, qty: 1 }); }
    };

    const removeItem = (index) => { cartList.value.splice(index, 1); };

    const clearMember = () => {
        currentMember.value = {};
        isWaiveCoupon.value = false;
        selectedCouponRule.value = null;
        usedCouponCount.value = 0;
    };

    const clearAll = () => {
        cartList.value = [];
        clearMember();
        manualDiscount.value = 0;
        paymentList.value = [];
    };

    const restoreOrder = (cartArray, memberObj) => {
        cartList.value = cartArray;
        currentMember.value = memberObj;
    };

    const submitOrder = async (orderData) => {
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

    return {
        // 状态暴露
        cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        // 算力结果暴露 (引擎输出)
        totalCount, totalAmount, totalCouponNeeded, actualCouponUsed, finalPayAmount,
        theoreticalCouponUsed, participatingAmount, paymentStats,
        // 动作暴露
        addToCart, removeItem, clearMember, clearAll, restoreOrder, submitOrder, getCartItemPrices
    };
}