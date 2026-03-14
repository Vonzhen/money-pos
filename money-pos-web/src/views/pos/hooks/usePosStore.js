import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import { trialCalculate } from "@/api/pos/pos"; // 引入新接口
import { debounce } from 'lodash-es';

// 🌟 保持原有的全局单例状态 (名字一个都不改)
const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

// 🌟 新增防御字段
const trialResult = ref(null);
const reqId = ref(null);

export function usePosStore() {

    // 1. 保留您原有的单品价格解析器
    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;
        let unitPrice = Number(item.salePrice) || 0;
        let unitCoupon = 0;
        if (levelCode) {
            if (item.levelPrices && item.levelPrices[levelCode] !== undefined) unitPrice = Number(item.levelPrices[levelCode]);
            if (item.levelCoupons && item.levelCoupons[levelCode] !== undefined) unitCoupon = Number(item.levelCoupons[levelCode]);
        }
        const theoreticalCoupon = unitCoupon;
        if (isWaiveCoupon.value) unitCoupon = 0;
        return { unitPrice, unitCoupon, theoreticalCoupon };
    };

    // 🌟 新增：后端镜像试算动作 (镜像化)
    const runTrial = debounce(async () => {
        if (cartList.value.length === 0) return;
        const payload = {
            member: currentMember.value.id || null,
            items: cartList.value.map(i => ({ goodsId: i.id, quantity: Number(i.qty) || 1 })),
            usedCouponRuleId: selectedCouponRule.value?.ruleId || null,
            usedCouponCount: usedCouponCount.value || 0,
            waiveCoupon: isWaiveCoupon.value,
            manualDiscountAmount: manualDiscount.value || 0
        };
        try {
            const res = await trialCalculate(payload);
            trialResult.value = res; // 拿到后端算出的“真理”
        } catch (e) { console.error("试算失败", e); }
    }, 300);

    // 2. 聚合计算：如果后端试算有结果，优先用后端的，否则回退到前端逻辑
    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 1), 0));
    const totalAmount = computed(() => {
        if (trialResult.value) return trialResult.value.totalAmount;
        return Number(cartList.value.reduce((acc, item) => acc + (getCartItemPrices(item, currentMember.value).unitPrice * (Number(item.qty) || 1)), 0).toFixed(2));
    });

    // 🌟 关键：最终应付以后端试算结果为准 (Mirror)
    const finalPayAmount = computed(() => {
        if (trialResult.value) return trialResult.value.finalPayAmount;
        // 以下是您的原始逻辑作为回退
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        const manualDeduct = Number(manualDiscount.value) || 0;
        let final = totalAmount.value - voucherDeduct - manualDeduct;
        return final > 0 ? Number(final.toFixed(2)) : 0;
    });

    const actualCouponUsed = computed(() => trialResult.value ? trialResult.value.couponDeduct : 0);
    const theoreticalCouponUsed = computed(() => {
        const sum = cartList.value.reduce((acc, item) => acc + (getCartItemPrices(item, currentMember.value).theoreticalCoupon * (Number(item.qty) || 1)), 0);
        return Number(sum.toFixed(2));
    });

    const participatingAmount = computed(() => {
        return Number(cartList.value.reduce((acc, item) => {
            if (item.isDiscountParticipable === 1) return acc + (getCartItemPrices(item, currentMember.value).unitPrice * (Number(item.qty) || 1));
            return acc;
        }, 0).toFixed(2));
    });

    const paymentStats = computed(() => {
        const targetPay = finalPayAmount.value;
        const payments = paymentList.value || [];
        const aggregate = Number((payments.find(p => p.code?.includes('AGGREGATE'))?.amount || 0).toFixed(2));
        const tendered = Number(payments.reduce((sum, p) => p.code?.includes('AGGREGATE') ? sum : sum + (Number(p.amount) || 0), 0).toFixed(2));
        const totalInputs = Number((tendered + aggregate).toFixed(2));
        const change = totalInputs > targetPay ? Number((totalInputs - targetPay).toFixed(2)) : 0;
        const unpaid = Math.max(0, Number((targetPay - totalInputs).toFixed(2)));
        return { targetPay, tendered, aggregate, change, totalInputs, unpaid };
    });

    // 🌟 幂等准备
    const prepareCheckout = () => { reqId.value = `REQ${Date.now()}`; };

    const addToCart = (goods) => {
        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) { exist.qty = (exist.qty || 1) + 1; }
        else { cartList.value.unshift({ ...goods, qty: 1 }); }
        runTrial(); // 变动即试算
    };

    const removeItem = (index) => { cartList.value.splice(index, 1); runTrial(); };

    const clearMember = () => {
        currentMember.value = {}; isWaiveCoupon.value = false;
        selectedCouponRule.value = null; usedCouponCount.value = 0;
        trialResult.value = null;
    };

    const clearAll = () => {
        cartList.value = []; clearMember();
        manualDiscount.value = 0; paymentList.value = [];
    };

    return {
        cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, actualCouponUsed, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, // 暴露新状态
        addToCart, removeItem, clearMember, clearAll, runTrial, prepareCheckout, getCartItemPrices
    };
}