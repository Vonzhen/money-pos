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

    // 🌟 【影子计算底座】获取本地预估单价 (不再区分扣券，只区分零售价和会员价)
    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        let unitOriginalPrice = Number(item.salePrice) || 0;
        let unitRealPrice = unitOriginalPrice; // 默认成交价为零售价

        // 只要是会员，直接锁定为会员价
        if (levelCode && item.levelPrices && item.levelPrices[levelCode] !== undefined) {
            unitRealPrice = Number(item.levelPrices[levelCode]);
        }

        return { unitOriginalPrice, unitRealPrice };
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
                // 兼容不同 axios 拦截器的包裹层
                if (realData && realData.code !== undefined && realData.data) {
                    realData = realData.data;
                } else if (realData && realData.data && realData.data.finalPayAmount !== undefined) {
                    realData = realData.data;
                }
                trialResult.value = realData; // 存入全新的 PricingResult 标准对象
            }
        } catch (error) {
            console.error("计价引擎同步失败:", error);
        } finally {
            // 只有最新版本的请求结束，才解除 Loading 状态
            if (version === currentTrialVersion) {
                isTrialing.value = false;
            }
        }
    }, 300); // 300ms 防抖，防止连击狂暴发请求

    // 🌟 核心防线：增强版购物车 (Computed 深度派生)
    // 结合了“本地影子计算(0ms响应)”与“后端真理覆盖(权威数据)”
    const enrichedCartList = computed(() => {
        return cartList.value.map(item => {
            const qty = Number(item.qty) || 1;
            const { unitOriginalPrice, unitRealPrice } = getCartItemPrices(item, currentMember.value);

            // 1. 本地影子计算 (双轨制：零售轨 & 会员轨)
            let displayOriginalPrice = unitOriginalPrice;
            let displayRealPrice = unitRealPrice;
            let displaySubtotalRetail = unitOriginalPrice * qty;
            let displaySubtotalMember = unitRealPrice * qty;
            let displaySubtotalPrivilege = displaySubtotalRetail - displaySubtotalMember; // 特权原值

            // 2. 后端权威覆盖 (一旦后端返回 PricingResult，静默替换为精准值)
            if (trialResult.value && trialResult.value.items) {
                const trialItem = trialResult.value.items.find(i => String(i.goodsId) === String(item.id));
                if (trialItem) {
                    displayOriginalPrice = trialItem.unitOriginalPrice !== undefined ? trialItem.unitOriginalPrice : displayOriginalPrice;
                    displayRealPrice = trialItem.unitRealPrice !== undefined ? trialItem.unitRealPrice : displayRealPrice;
                    displaySubtotalRetail = trialItem.subTotalRetail !== undefined ? trialItem.subTotalRetail : displaySubtotalRetail;
                    displaySubtotalMember = trialItem.subTotalMember !== undefined ? trialItem.subTotalMember : displaySubtotalMember;
                    displaySubtotalPrivilege = trialItem.subTotalPrivilege !== undefined ? trialItem.subTotalPrivilege : displaySubtotalPrivilege;
                }
            }

            return {
                ...item,
                qty,
                displayOriginalPrice,
                displayRealPrice,
                displaySubtotalRetail,
                displaySubtotalMember,
                displaySubtotalPrivilege,
                // 🌟 向下兼容旧的 Vue 模板字段，防止报错
                displayPrice: displayRealPrice,
                displaySubtotal: displaySubtotalMember,
                displayCouponDeduct: displaySubtotalPrivilege,
                isPending: isTrialing.value // 供 UI 渲染呼吸灯使用
            };
        });
    });

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => enrichedCartList.value.reduce((sum, item) => sum + item.qty, 0));

    // ==========================================
    // 🌟 核心计价指标映射 (优先取后端真理，降级用影子计算)
    // ==========================================

    // 1. 零售价合计 (原 totalAmount 语义)
    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.retailAmount !== undefined && !isTrialing.value) {
            return trialResult.value.retailAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => sum + item.displaySubtotalRetail, 0).toFixed(2));
    });

    // 2. 会员价合计 (新的应付底座)
    const memberAmount = computed(() => {
        if (trialResult.value && trialResult.value.memberAmount !== undefined && !isTrialing.value) {
            return trialResult.value.memberAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => sum + item.displaySubtotalMember, 0).toFixed(2));
    });

    // 3. 理论应扣特权原值 (不管免收开关怎么变，这个值恒定！免收开关再也不会消失了)
    const theoreticalCouponUsed = computed(() => {
        if (trialResult.value && trialResult.value.privilegeAmount !== undefined && !isTrialing.value) {
            return trialResult.value.privilegeAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => sum + item.displaySubtotalPrivilege, 0).toFixed(2));
    });

    // 4. 真实扣券额 (后端裁决，前端不瞎猜)
    const actualCouponUsed = computed(() => trialResult.value ? (trialResult.value.actualCouponDeduct || 0) : 0);

    // 5. 店铺免收承担额 (后端裁决)
    const waivedCouponAmount = computed(() => trialResult.value ? (trialResult.value.waivedCouponAmount || 0) : 0);

    // 6. 参与满减的金额底座 (基于会员价计算)
    const participatingAmount = computed(() => {
        if (trialResult.value && trialResult.value.participatingAmount !== undefined && !isTrialing.value) {
            return trialResult.value.participatingAmount;
        }
        return Number(enrichedCartList.value.reduce((sum, item) => {
            return item.isDiscountParticipable === 1 ? sum + item.displaySubtotalMember : sum;
        }, 0).toFixed(2));
    });

    // 7. 最终实付额 (基于 memberAmount 扣减)
    const finalPayAmount = computed(() => {
        if (trialResult.value && trialResult.value.finalPayAmount !== undefined && !isTrialing.value) {
            return trialResult.value.finalPayAmount;
        }
        // 影子计算：会员价基准 - 手工折扣 - 满减券
        const manualDeduct = Number(manualDiscount.value) || 0;
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        let final = memberAmount.value - manualDeduct - voucherDeduct;
        return final > 0 ? Number(final.toFixed(2)) : 0;
    });

    // ==========================================
    // 支付流水与交互逻辑 (保持原样)
    // ==========================================

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
        // 🌟 导出所有状态 (包含新引入的双轨字段)
        cartList, enrichedCartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, memberAmount, actualCouponUsed, waivedCouponAmount, finalPayAmount, theoreticalCouponUsed, participatingAmount, paymentStats,
        reqId, trialResult, isTrialing,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo
    };
}