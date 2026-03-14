import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import { debounce } from 'lodash-es';

// 🌟 全局单例状态
const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

const trialResult = ref(null);
const reqId = ref('');
const isTrialing = ref(false);

export function usePosStore() {

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
        return { unitPrice, unitCoupon };
    };

    const runTrial = debounce(async () => {
        if (cartList.value.length === 0) {
            trialResult.value = null;
            return;
        }

        isTrialing.value = true;
        const payload = {
            member: currentMember.value.id || null,
            usedCouponRuleId: selectedCouponRule.value ? selectedCouponRule.value.ruleId : null,
            usedCouponCount: usedCouponCount.value || 0,
            waiveCoupon: isWaiveCoupon.value,
            manualDiscountAmount: manualDiscount.value || 0,
            items: cartList.value.map(item => ({
                goodsId: item.id,
                quantity: Number(item.qty) || 1
            }))
        };

        try {
            const res = await req({ url: '/pos/trial', method: 'POST', data: payload });
            let realData = res;
            if (realData && realData.code !== undefined && realData.data) {
                realData = realData.data;
            } else if (realData && realData.data && realData.data.finalPayAmount !== undefined) {
                realData = realData.data;
            }
            trialResult.value = realData;
        } catch (error) {
            console.error("计价引擎同步失败:", error);
        } finally {
            isTrialing.value = false;
        }
    }, 300);

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 1), 0));

    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.totalAmount !== undefined) return trialResult.value.totalAmount;
        return Number(cartList.value.reduce((sum, item) => sum + (getCartItemPrices(item, currentMember.value).unitPrice * (Number(item.qty) || 1)), 0).toFixed(2));
    });

    const finalPayAmount = computed(() => {
        if (trialResult.value && trialResult.value.finalPayAmount !== undefined) return trialResult.value.finalPayAmount;
        const manualDeduct = Number(manualDiscount.value) || 0;
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        let final = totalAmount.value - manualDeduct - voucherDeduct;
        return final > 0 ? Number(final.toFixed(2)) : 0;
    });

    const participatingAmount = computed(() => {
        if (trialResult.value && trialResult.value.participatingAmount !== undefined) return trialResult.value.participatingAmount;
        return Number(cartList.value.reduce((sum, item) => {
            if (item.isDiscountParticipable === 1) return sum + (getCartItemPrices(item, currentMember.value).unitPrice * (Number(item.qty) || 1));
            return sum;
        }, 0).toFixed(2));
    });

    const actualCouponUsed = computed(() => trialResult.value ? (trialResult.value.memberCouponDeduct || 0) : 0);

    const theoreticalCouponUsed = computed(() => {
        const sum = cartList.value.reduce((acc, item) => acc + (getCartItemPrices(item, currentMember.value).unitCoupon * (Number(item.qty) || 1)), 0);
        return Number(sum.toFixed(2));
    });

    const paymentStats = computed(() => {
        const targetPay = Number(finalPayAmount.value) || 0;
        const payments = paymentList.value || [];
        const aggregate = Number((payments.find(p => p.code && p.code.includes('AGGREGATE'))?.amount || 0).toFixed(2));
        const tendered = Number(payments.reduce((sum, p) => p.code && p.code.includes('AGGREGATE') ? sum : sum + (Number(p.amount) || 0), 0).toFixed(2));
        const totalInputs = Number((tendered + aggregate).toFixed(2));
        const change = totalInputs > targetPay ? Number((totalInputs - targetPay).toFixed(2)) : 0;
        const unpaid = Math.max(0, Number((targetPay - totalInputs).toFixed(2)));
        return { targetPay, tendered, aggregate, change, totalInputs, unpaid };
    });

    const prepareCheckout = () => { reqId.value = `REQ${Date.now()}`; };

    const addToCart = (goods) => {
        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) { exist.qty = (exist.qty || 1) + 1; }
        else {
            // 🌟 恢复为您习惯的先扫的在上面
            cartList.value.push({ ...goods, qty: 1 });
        }
        runTrial();
    };

    const removeItem = (index) => {
        cartList.value.splice(index, 1);
        runTrial();
    };

    // 🌟 规范绑定动作
    const bindMember = (memberObj) => {
        currentMember.value = memberObj;
        isWaiveCoupon.value = false;
        selectedCouponRule.value = null;
        usedCouponCount.value = 0;
        runTrial();
    };

    const clearMember = () => {
        currentMember.value = {};
        isWaiveCoupon.value = false;
        selectedCouponRule.value = null;
        usedCouponCount.value = 0;
        runTrial();
    };

    const clearAll = () => {
        cartList.value = [];
        clearMember();
        manualDiscount.value = 0;
        paymentList.value = [];
        trialResult.value = null;
    };

    const restoreOrder = (cartArray, memberObj) => {
        cartList.value = cartArray;
        currentMember.value = memberObj;
        runTrial();
    };

    const submitOrder = async (orderData) => {
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

    // 🌟 唯一的大收口 Return，绝对不允许中间被截断！
    return {
        cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, actualCouponUsed, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, isTrialing,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo
    };
}