import { ref, computed } from 'vue';
import { SettleEngine } from '../engine/settleEngine';
import { req } from "@/api/index.js";

const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

export function usePosStore() {
    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + item.quantity, 0));
    const totalAmount = computed(() => cartList.value.reduce((sum, item) => sum + (SettleEngine.getRealPrice(item, currentMember.value) * item.quantity), 0));

    const totalCouponNeeded = computed(() => {
        if (!currentMember.value.id) return 0;
        return cartList.value.reduce((sum, item) => {
            const diff = (item.salePrice || 0) - SettleEngine.getRealPrice(item, currentMember.value);
            return sum + (diff > 0 ? diff * item.quantity : 0);
        }, 0);
    });

    const actualCouponUsed = computed(() => isWaiveCoupon.value ? 0 : totalCouponNeeded.value);
    const finalPayAmount = computed(() => SettleEngine.calculateFinalPayAmount(totalAmount.value, selectedCouponRule.value, usedCouponCount.value, manualDiscount.value));

    const addToCart = (goods) => {
        // 🌟 埋点侦测：把后端传过来的商品打印到控制台，看看有没有带会员价！
        console.log("🛒 扫码获取到的商品原始数据：", goods);
        if (!goods.levelPrices || Object.keys(goods.levelPrices).length === 0) {
            console.warn("⚠️ 警告：该商品没有携带 levelPrices 数据！如果是会员商品，说明后端的【扫码/搜索】接口漏发了数据！");
        }

        const exist = cartList.value.find(item => item.id === goods.id);
        if (exist) { exist.quantity += 1; }
        else {
            cartList.value.unshift({ ...goods, salePrice: goods.salePrice || 0, vipPrice: goods.vipPrice || goods.salePrice || 0, levelPrices: goods.levelPrices || {}, quantity: 1 });
        }
    };

    const clearMember = () => {
        currentMember.value = {}; isWaiveCoupon.value = false; selectedCouponRule.value = null; usedCouponCount.value = 0;
    };

    const clearAll = () => {
        cartList.value = []; clearMember(); manualDiscount.value = 0;
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
        addToCart, clearMember, clearAll, restoreOrder, submitOrder
    };
}