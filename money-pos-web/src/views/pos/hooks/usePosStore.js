import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import { debounce } from 'lodash-es';

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

export function usePosStore() {

    // 获取本地预估单价 (影子计算底座)
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

    // 🌟 核心引擎：防抖 + 版本控制的试算请求
    const runTrial = debounce(async () => {
        if (cartList.value.length === 0) {
            trialResult.value = null;
            return;
        }

        // 生成当前请求的唯一版本戳
        const version = Date.now();
        currentTrialVersion = version;
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

            // 🌟 安全红线 2：丢弃过期响应。只有当前版本是最新的，才允许覆盖本地状态！
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
            // 只有最新版本的请求结束，才解除 Loading 状态
            if (version === currentTrialVersion) {
                isTrialing.value = false;
            }
        }
    }, 300); // 🌟 300ms 防抖，防止连击狂暴发请求

    // 🌟 核心防线：增强版购物车 (Computed 深度派生)
    // 结合了“本地预计算(0ms响应)”与“后端试算覆盖(权威数据)”
    const enrichedCartList = computed(() => {
        return cartList.value.map(item => {
            const qty = Number(item.qty) || 1;
            const { unitPrice, unitCoupon } = getCartItemPrices(item, currentMember.value);

            // 1. 本地影子计算 (立即响应)
            let displayPrice = unitPrice;
            let displaySubtotal = unitPrice * qty;
            let displayCouponDeduct = unitCoupon * qty;

            // 2. 后端权威覆盖 (一旦后端返回对应数据，静默替换为精准值)
            if (trialResult.value && trialResult.value.items) {
                const trialItem = trialResult.value.items.find(i => String(i.goodsId) === String(item.id));
                if (trialItem) {
                    displayPrice = trialItem.realPrice !== undefined ? trialItem.realPrice : displayPrice;
                    displaySubtotal = trialItem.subTotal !== undefined ? trialItem.subTotal : displaySubtotal;
                    displayCouponDeduct = trialItem.couponDeduct !== undefined ? trialItem.couponDeduct : displayCouponDeduct;
                }
            }

            return {
                ...item,
                qty,
                displayPrice,
                displaySubtotal,
                displayCouponDeduct,
                isPending: isTrialing.value // 供 UI 渲染呼吸灯使用，提示数据正在后端精算中
            };
        });
    });

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => enrichedCartList.value.reduce((sum, item) => sum + item.qty, 0));

    // 🌟 总金额优先取试算结果，没有则降级为本地影子总额
    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.totalAmount !== undefined && !isTrialing.value) {
            return trialResult.value.totalAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => sum + item.displaySubtotal, 0).toFixed(2));
    });

    const finalPayAmount = computed(() => {
        if (trialResult.value && trialResult.value.finalPayAmount !== undefined && !isTrialing.value) {
            return trialResult.value.finalPayAmount;
        }
        const manualDeduct = Number(manualDiscount.value) || 0;
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        let final = totalAmount.value - manualDeduct - voucherDeduct;
        return final > 0 ? Number(final.toFixed(2)) : 0;
    });

    const participatingAmount = computed(() => {
        if (trialResult.value && trialResult.value.participatingAmount !== undefined && !isTrialing.value) {
            return trialResult.value.participatingAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => {
            return item.isDiscountParticipable === 1 ? sum + item.displaySubtotal : sum;
        }, 0).toFixed(2));
    });

    const actualCouponUsed = computed(() => trialResult.value ? (trialResult.value.memberCouponDeduct || 0) : 0);

    const theoreticalCouponUsed = computed(() => {
        return Number(enrichedCartList.value.reduce((sum, item) => sum + item.displayCouponDeduct, 0).toFixed(2));
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
            cartList.value.push({ ...goods, qty: 1 });
        }
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

    return {
        // 🌟 导出原始状态与增强列表
        cartList, enrichedCartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, actualCouponUsed, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, isTrialing,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo
    };
}