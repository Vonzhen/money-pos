import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import Big from 'big.js';

// ==========================================
// 🌟 全局单例状态 (跨组件共享，必须放在函数外！)
// ==========================================
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

const globalBrandsKv = ref({});
const globalMemberTypes = ref([]);

// 🌟 全局唯一的选中焦点
const activeItemIndex = ref(-1);

let currentTrialVersion = 0;
let trialTimer = null;
let trialResolvers = [];

export function usePosStore() {

    const initGlobalDicts = (brandsKv, memberTypes) => {
        globalBrandsKv.value = brandsKv;
        globalMemberTypes.value = memberTypes;
    };

    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        let unitOriginalPrice = new Big(item.salePrice || 0);
        let unitRealPrice = unitOriginalPrice;
        let unitCoupon = new Big(0); // 🌟 核心修正：新增单品应扣券额变量

        if (levelCode && item.levelPrices && item.levelPrices[levelCode] !== undefined) {
            unitRealPrice = new Big(item.levelPrices[levelCode]);

            // 🌟 核心修正：尝试精准读取数据库配置的真实券额（单轨模式这里会读到 0）
            if (item.levelCoupons && item.levelCoupons[levelCode] !== undefined) {
                unitCoupon = new Big(item.levelCoupons[levelCode]);
            } else if (item.memberCoupon !== undefined) {
                unitCoupon = new Big(item.memberCoupon);
            } else {
                // 兜底逻辑：如果前端完全没拿到券额字段，才退化为物理差价
                const diff = unitOriginalPrice.minus(unitRealPrice);
                unitCoupon = diff.gt(0) ? diff : new Big(0);
            }
        }
        return { unitOriginalPrice, unitRealPrice, unitCoupon };
    };

    const runTrial = () => {
        return new Promise((resolve) => {
            trialResolvers.push(resolve);
            isTrialing.value = true;
            clearTimeout(trialTimer);

            trialTimer = setTimeout(async () => {
                if (cartList.value.length === 0) {
                    trialResult.value = null;
                    isTrialing.value = false;
                    flushTrialResolvers();
                    return;
                }

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
                    if (version === currentTrialVersion) {
                        let realData = res.data || res;
                        trialResult.value = realData;
                    }
                } catch (error) {
                    console.error("计价引擎同步失败:", error);
                } finally {
                    if (version === currentTrialVersion) {
                        isTrialing.value = false;
                        flushTrialResolvers();
                    }
                }
            }, 300);
        });
    };

    const flushTrialResolvers = () => {
        const resolvers = trialResolvers;
        trialResolvers = [];
        resolvers.forEach(r => r());
    };

    const enrichedCartList = computed(() => {
        return cartList.value.map(item => {
            const qty = new Big(item.qty || 1);
            // 🌟 接收拆分后的精准价和券
            const { unitOriginalPrice, unitRealPrice, unitCoupon } = getCartItemPrices(item, currentMember.value);

            let displaySubtotalRetail = unitOriginalPrice.times(qty);
            let displaySubtotalMember = unitRealPrice.times(qty);
            let displaySubtotalPrivilege = displaySubtotalRetail.minus(displaySubtotalMember); // 这是物理让利差价
            let displayCouponDeduct = unitCoupon.times(qty); // 🌟 独立出来：这是真正要在列表里显示的“应扣券额”

            if (trialResult.value && trialResult.value.items) {
                const trialItem = trialResult.value.items.find(i => String(i.goodsId) === String(item.id));
                if (trialItem) {
                    displaySubtotalRetail = new Big(trialItem.subTotalRetail ?? displaySubtotalRetail.toNumber());
                    displaySubtotalMember = new Big(trialItem.subTotalMember ?? displaySubtotalMember.toNumber());
                    displaySubtotalPrivilege = new Big(trialItem.subTotalPrivilege ?? displaySubtotalPrivilege.toNumber());
                }
            }

            // 🌟 终极单轨兜底校验：如果后端引擎说整单实际不扣券(且没开免收)，前端列表展示的券额强制全归 0！
            if (trialResult.value && !isWaiveCoupon.value && trialResult.value.actualCouponDeduct === 0) {
                displayCouponDeduct = new Big(0);
            }

            return {
                ...item,
                qty: qty.toNumber(),
                displayOriginalPrice: unitOriginalPrice.toNumber(),
                displayRealPrice: unitRealPrice.toNumber(),
                displaySubtotalRetail: displaySubtotalRetail.toNumber(),
                displaySubtotalMember: displaySubtotalMember.toNumber(),
                displaySubtotalPrivilege: displaySubtotalPrivilege.toNumber(), // 后端算出来的让利
                displayPrice: unitRealPrice.toNumber(),
                displaySubtotal: displaySubtotalMember.toNumber(),
                displayCouponDeduct: displayCouponDeduct.toNumber(), // 🌟 给 CartTable 渲染用的单品券额
                isPending: isTrialing.value
            };
        });
    });

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 0), 0));

    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.retailAmount !== undefined && !isTrialing.value) return trialResult.value.retailAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalRetail), new Big(0)).toNumber();
    });

    const memberAmount = computed(() => {
        if (trialResult.value && trialResult.value.memberAmount !== undefined && !isTrialing.value) return trialResult.value.memberAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalMember), new Big(0)).toNumber();
    });

    // 🌟 核心逻辑修复：重新定义“理论会员券抵扣额” (用来判断余额够不够，以及是否显示免收开关)
    const theoreticalCouponUsed = computed(() => {
        // 1. 先用前端精准的公式自己算一遍底子
        const frontendCalculatedCoupon = enrichedCartList.value.reduce((sum, item) => sum.plus(item.displayCouponDeduct || 0), new Big(0)).toNumber();

        if (trialResult.value && !isTrialing.value) {
            // 2. 如果没有开启“免收券”，我们绝对信任后端的 actualCouponDeduct
            if (!isWaiveCoupon.value && trialResult.value.actualCouponDeduct !== undefined) {
                return trialResult.value.actualCouponDeduct;
            }
            // 3. 如果开启了“免收券”，后端的 actualCoupon 会变成 0。
            // 为了维持 UI 开关不消失，我们返回前端算出的底子。
        }
        return frontendCalculatedCoupon;
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

    // 🌟 这是绝对不能丢的 PaymentStats！
    const paymentStats = computed(() => {
        const targetPay = new Big(finalPayAmount.value || 0);
        const payments = paymentList.value || [];
        const aggregate = new Big(payments.find(p => p.code && p.code.includes('AGGREGATE'))?.amount || 0);
        const tendered = payments.reduce((sum, p) => p.code && p.code.includes('AGGREGATE') ? sum : sum.plus(p.amount || 0), new Big(0));
        const totalInputs = tendered.plus(aggregate);
        const change = totalInputs.gt(targetPay) ? totalInputs.minus(targetPay) : new Big(0);
        const unpaid = targetPay.gt(totalInputs) ? targetPay.minus(targetPay) : new Big(0);
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
        const existIndex = cartList.value.findIndex(item => item.id === goods.id);
        if (existIndex !== -1) {
            cartList.value[existIndex].qty = (Number(cartList.value[existIndex].qty) || 1) + 1;
            activeItemIndex.value = existIndex;
        } else {
            cartList.value.push({ ...goods, qty: 1 });
            activeItemIndex.value = cartList.value.length - 1;
        }
        runTrial();
    };

    const moveActiveIndex = (step) => {
        if (cartList.value.length === 0) return;
        let newIdx = activeItemIndex.value + step;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= cartList.value.length) newIdx = cartList.value.length - 1;
        activeItemIndex.value = newIdx;
    };

    const quickAdjustActiveItem = (delta) => {
        if (cartList.value.length === 0 || activeItemIndex.value === -1) return;
        const item = cartList.value[activeItemIndex.value];
        if (!item) return;

        const newQty = (Number(item.qty) || 1) + delta;
        if (newQty >= 1) {
            item.qty = newQty;
            runTrial();
        } else {
            removeItem(activeItemIndex.value);
        }
    };

    const removeItem = (index) => {
        cartList.value.splice(index, 1);
        activeItemIndex.value = cartList.value.length > 0 ? cartList.value.length - 1 : -1;
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
        currentTrialVersion = 0;
        activeItemIndex.value = -1;
    };

    const restoreOrder = (cartArray, memberObj) => {
        cartList.value = cartArray;
        currentMember.value = memberObj;
        activeItemIndex.value = cartArray.length > 0 ? cartArray.length - 1 : -1;
        runTrial();
    };

    const submitOrder = async (orderData) => {
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

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
        totalCount, totalAmount, memberAmount, actualCouponUsed, waivedCouponAmount, finalPayAmount, theoreticalCouponUsed, participatingAmount,
        paymentStats,
        reqId, trialResult, isTrialing, activeItemIndex,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo, scanAndAddToCart, globalBrandsKv, globalMemberTypes, initGlobalDicts,
        quickAdjustActiveItem, moveActiveIndex
    };
}