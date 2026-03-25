import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import Big from 'big.js'; // 🌟 引入高精度计算库，替换原有的 lodash debounce

// 🌟 全局单例状态 (跨组件共享)
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

// 🌟 安全红线 1：请求版本锁。防止并发修改导致旧请求覆盖新请求
let currentTrialVersion = 0;
// 🌟 高级异步防抖控制器 (P1-1 修复)
let trialTimer = null;
let trialResolvers = [];

export function usePosStore() {

    // 🌟 【影子计算底座】获取本地预估单价 (使用 Big.js 修复 P2-2)
    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        let unitOriginalPrice = new Big(item.salePrice || 0);
        let unitRealPrice = unitOriginalPrice; // 默认成交价为零售价

        // 只要是会员，直接锁定为会员价
        if (levelCode && item.levelPrices && item.levelPrices[levelCode] !== undefined) {
            unitRealPrice = new Big(item.levelPrices[levelCode]);
        }

        return { unitOriginalPrice, unitRealPrice };
    };

    // 🌟 核心引擎：Promise 驱动的试算请求 (P1-1 修复)
    const runTrial = () => {
        return new Promise((resolve) => {
            trialResolvers.push(resolve);
            isTrialing.value = true; // 立即上锁
            clearTimeout(trialTimer);

            trialTimer = setTimeout(async () => {
                if (cartList.value.length === 0) {
                    trialResult.value = null;
                    isTrialing.value = false;
                    flushTrialResolvers();
                    return;
                }

                // 生成当前请求的唯一版本戳
                const version = Date.now();
                currentTrialVersion = version;

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

                    // 🌟 安全红线 2：丢弃过期响应
                    if (version === currentTrialVersion) {
                        let realData = res;
                        if (realData && realData.code !== undefined && realData.data) {
                            realData = realData.data;
                        } else if (realData && realData.data && realData.data.finalPayAmount !== undefined) {
                            realData = realData.data;
                        }
                        trialResult.value = realData;
                    }
                } catch (error) {
                    console.error("计价引擎同步失败:", error);
                } finally {
                    if (version === currentTrialVersion) {
                        isTrialing.value = false;
                        flushTrialResolvers(); // 释放等待的 await
                    }
                }
            }, 300); // 300ms 防抖
        });
    };

    const flushTrialResolvers = () => {
        const resolvers = trialResolvers;
        trialResolvers = [];
        resolvers.forEach(r => r());
    };

    // 🌟 核心防线：增强版购物车 (引入 Big.js 处理 display 逻辑)
    const enrichedCartList = computed(() => {
        return cartList.value.map(item => {
            const qty = new Big(item.qty || 1);
            const { unitOriginalPrice, unitRealPrice } = getCartItemPrices(item, currentMember.value);

            let displaySubtotalRetail = unitOriginalPrice.times(qty);
            let displaySubtotalMember = unitRealPrice.times(qty);
            let displaySubtotalPrivilege = displaySubtotalRetail.minus(displaySubtotalMember);

            // 2. 后端权威覆盖
            if (trialResult.value && trialResult.value.items) {
                const trialItem = trialResult.value.items.find(i => String(i.goodsId) === String(item.id));
                if (trialItem) {
                    displaySubtotalRetail = new Big(trialItem.subTotalRetail ?? displaySubtotalRetail.toNumber());
                    displaySubtotalMember = new Big(trialItem.subTotalMember ?? displaySubtotalMember.toNumber());
                    displaySubtotalPrivilege = new Big(trialItem.subTotalPrivilege ?? displaySubtotalPrivilege.toNumber());
                }
            }

            return {
                ...item,
                qty: qty.toNumber(),
                displayOriginalPrice: unitOriginalPrice.toNumber(),
                displayRealPrice: unitRealPrice.toNumber(),
                displaySubtotalRetail: displaySubtotalRetail.toNumber(),
                displaySubtotalMember: displaySubtotalMember.toNumber(),
                displaySubtotalPrivilege: displaySubtotalPrivilege.toNumber(),
                displayPrice: unitRealPrice.toNumber(),
                displaySubtotal: displaySubtotalMember.toNumber(),
                displayCouponDeduct: displaySubtotalPrivilege.toNumber(),
                isPending: isTrialing.value
            };
        });
    });

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => enrichedCartList.value.reduce((sum, item) => sum + item.qty, 0));

    // ==========================================
    // 🌟 核心计价指标映射 (Big.js 保护)
    // ==========================================

    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.retailAmount !== undefined && !isTrialing.value) return trialResult.value.retailAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalRetail), new Big(0)).toNumber();
    });

    const memberAmount = computed(() => {
        if (trialResult.value && trialResult.value.memberAmount !== undefined && !isTrialing.value) return trialResult.value.memberAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalMember), new Big(0)).toNumber();
    });

    const theoreticalCouponUsed = computed(() => {
        if (trialResult.value && trialResult.value.privilegeAmount !== undefined && !isTrialing.value) return trialResult.value.privilegeAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalPrivilege), new Big(0)).toNumber();
    });

    const actualCouponUsed = computed(() => trialResult.value ? (trialResult.value.actualCouponDeduct || 0) : 0);
    const waivedCouponAmount = computed(() => trialResult.value ? (trialResult.value.waivedCouponAmount || 0) : 0);

    const participatingAmount = computed(() => {
        if (trialResult.value && trialResult.value.participatingAmount !== undefined && !isTrialing.value) return trialResult.value.participatingAmount;
        return enrichedCartList.value.reduce((sum, item) => {
            return item.isDiscountParticipable === 1 ? sum.plus(item.displaySubtotalMember) : sum;
        }, new Big(0)).toNumber();
    });

    const finalPayAmount = computed(() => {
        if (trialResult.value && trialResult.value.finalPayAmount !== undefined && !isTrialing.value) {
            return trialResult.value.finalPayAmount;
        }
        const manualDeduct = new Big(manualDiscount.value || 0);
        const voucherDeduct = new Big(selectedCouponRule.value?.deduction || 0).times(usedCouponCount.value || 0);
        const final = new Big(memberAmount.value).minus(manualDeduct).minus(voucherDeduct);
        return final.gt(0) ? final.toNumber() : 0;
    });

    const paymentStats = computed(() => {
        const targetPay = new Big(finalPayAmount.value || 0);
        const payments = paymentList.value || [];

        const aggregate = new Big(payments.find(p => p.code && p.code.includes('AGGREGATE'))?.amount || 0);
        const tendered = payments.reduce((sum, p) => p.code && p.code.includes('AGGREGATE') ? sum : sum.plus(p.amount || 0), new Big(0));

        const totalInputs = tendered.plus(aggregate);
        const change = totalInputs.gt(targetPay) ? totalInputs.minus(targetPay) : new Big(0);
        const unpaid = targetPay.gt(totalInputs) ? targetPay.minus(totalInputs) : new Big(0);

        return {
            targetPay: targetPay.toNumber(),
            tendered: tendered.toNumber(),
            aggregate: aggregate.toNumber(),
            change: change.toNumber(),
            totalInputs: totalInputs.toNumber(),
            unpaid: unpaid.toNumber()
        };
    });

    const prepareCheckout = () => { reqId.value = `REQ${Date.now()}`; };

    const addToCart = (goods) => {
        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) { exist.qty = (exist.qty || 1) + 1; }
        else { cartList.value.push({ ...goods, qty: 1 }); }
        runTrial();
    };

    const removeItem = (index) => {
        cartList.value.splice(index, 1);
        runTrial();
    };

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
        currentTrialVersion = 0; // 重置版本锁
    };

    const restoreOrder = (cartArray, memberObj) => {
        cartList.value = cartArray;
        currentMember.value = memberObj;
        runTrial();
    };

    const submitOrder = async (orderData) => {
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

// 🌟 核心新增：暴露按条码搜索并加入购物车的能力，供外部扫码枪接管
    const scanAndAddToCart = async (barcode) => {
        try {
            const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: barcode } });
            const items = res.data || [];
            if (items.length === 1) {
                addToCart(items[0]);
                return { success: true, goods: items[0] };
            } else if (items.length > 1) {
                return { success: false, reason: 'multiple', items };
            } else {
                return { success: false, reason: 'not_found', barcode };
            }
        } catch (e) {
            return { success: false, reason: 'error', error: e };
        }
    };

    return {
        cartList, enrichedCartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, memberAmount, actualCouponUsed, waivedCouponAmount, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, isTrialing,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo,
        scanAndAddToCart // 🌟 暴露出扫码方法
    };

    return {
        cartList, enrichedCartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, memberAmount, actualCouponUsed, waivedCouponAmount, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, isTrialing,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo
    };
}