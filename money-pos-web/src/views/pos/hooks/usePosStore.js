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

        if (isWaiveCoupon.value) {
            unitCoupon = 0;
        }

        return { unitPrice, unitCoupon };
    };

    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 1), 0));

    // 🌟 这里的 totalAmount 已经是最终需要收取的现金部分！
    const totalAmount = computed(() => {
        return cartList.value.reduce((sum, item) => {
            const { unitPrice } = getCartItemPrices(item, currentMember.value);
            return sum + (unitPrice * (Number(item.qty) || 1));
        }, 0);
    });

    // 这里是需要从会员账户里扣除的虚拟券资产（与现金无关）
    const actualCouponUsed = computed(() => {
        if (isWaiveCoupon.value) return 0;
        return cartList.value.reduce((sum, item) => {
            const { unitCoupon } = getCartItemPrices(item, currentMember.value);
            return sum + (unitCoupon * (Number(item.qty) || 1));
        }, 0);
    });

    const totalCouponNeeded = computed(() => actualCouponUsed.value);

    // 🌟 核心致命修复：只减去满减券和手动优惠，绝不再减会员券！
    const finalPayAmount = computed(() => {
        const voucherDeduct = (selectedCouponRule.value?.deduction || 0) * (usedCouponCount.value || 0);
        const manualDeduct = Number(manualDiscount.value) || 0;

        let final = totalAmount.value - voucherDeduct - manualDeduct;
        return final > 0 ? final : 0;
    });

    const addToCart = (goods) => {
        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) {
            exist.qty = (exist.qty || 1) + 1;
        } else {
            cartList.value.unshift({ ...goods, qty: 1 });
        }
    };

    const removeItem = (index) => {
        cartList.value.splice(index, 1);
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
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

    return {
        cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, totalCouponNeeded, actualCouponUsed, finalPayAmount,
        addToCart, removeItem, clearMember, clearAll, restoreOrder, submitOrder,
        getCartItemPrices
    };
}