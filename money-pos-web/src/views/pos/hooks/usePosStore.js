import { ref, computed } from 'vue';
import { req } from "@/api/index.js";

const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

export function usePosStore() {

    // ==========================================
    // 🌟 核心透视眼：纯粹的数据提取器 (绝不做加减法推算)
    // ==========================================
    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        // 1. 查身份：提取该会员在这个商品品牌下的专属等级 (比如：宛伊 -> ANGEL)
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        // 默认散客状态
        let unitPrice = Number(item.salePrice) || 0;
        let unitCoupon = 0;

        // 2. 定盘子：如果该会员有等级，直接从后端的“价格矩阵”和“券矩阵”里拿现成的数据！
        if (levelCode) {
            if (item.levelPrices && item.levelPrices[levelCode] !== undefined) {
                unitPrice = Number(item.levelPrices[levelCode]);
            }
            if (item.levelCoupons && item.levelCoupons[levelCode] !== undefined) {
                unitCoupon = Number(item.levelCoupons[levelCode]);
            }
        }

        // 3. 强制免券开关
        if (isWaiveCoupon.value) {
            unitCoupon = 0;
        }

        return { unitPrice, unitCoupon };
    };

    // ==========================================
    // 📊 汇总计算：只做最简单的数量相乘
    // ==========================================

    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + item.quantity, 0));

    // 总计应收 (直接用提取出来的 unitPrice * 数量)
    const totalAmount = computed(() => {
        return cartList.value.reduce((sum, item) => {
            const { unitPrice } = getCartItemPrices(item, currentMember.value);
            return sum + (unitPrice * item.quantity);
        }, 0);
    });

    // 实际需扣会员券 (直接用提取出来的 unitCoupon * 数量)
    const actualCouponUsed = computed(() => {
        if (isWaiveCoupon.value) return 0;
        return cartList.value.reduce((sum, item) => {
            const { unitCoupon } = getCartItemPrices(item, currentMember.value);
            return sum + (unitCoupon * item.quantity);
        }, 0);
    });

    // 兼容你原有的变量名
    const totalCouponNeeded = computed(() => actualCouponUsed.value);

    // 最终实付金额 = 总计应收 - 会员券抵扣 - 满减券核销 - 整单优惠
    const finalPayAmount = computed(() => {
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        const manualDeduct = Number(manualDiscount.value) || 0;

        let final = totalAmount.value - actualCouponUsed.value - voucherDeduct - manualDeduct;
        return final > 0 ? final : 0;
    });

    // ==========================================
    // 🛒 购物车操作指令
    // ==========================================

    const addToCart = (goods) => {
        // 🌟 埋点侦测
        console.log("🛒 扫码入车原始数据：", goods);

        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) {
            exist.quantity += 1;
        } else {
            // 扔进购物车，不再强行赋 vipPrice 这种历史包袱变量
            cartList.value.unshift({ ...goods, quantity: 1 });
        }
    };

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
        // 提交订单给后端，后端的 SettleEngine 将进行绝对权威的二次核算并落库
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

    return {
        cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, totalCouponNeeded, actualCouponUsed, finalPayAmount,
        addToCart, clearMember, clearAll, restoreOrder, submitOrder,
        getCartItemPrices // 暴露给外部组件(如 CartTable.vue)，供它渲染单品价格时调用
    };
}