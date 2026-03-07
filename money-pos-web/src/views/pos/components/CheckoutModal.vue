<template>
    <el-dialog v-model="visible" title="混合收银工作站" width="900px" top="6vh" destroy-on-close class="checkout-dialog" @closed="$emit('closed')">
        <div class="flex gap-4 p-4 pb-0 h-[480px]">
            <div class="w-[42%] flex flex-col gap-3">
                <div v-if="currentMember.id" class="bg-gradient-to-br from-blue-50 to-indigo-50 p-4 rounded-lg border border-blue-200 shadow-inner shrink-0">
                    <div class="flex items-center gap-3 mb-3 border-b border-blue-100 pb-3">
                        <el-avatar :size="45" class="bg-blue-500 font-bold text-lg">{{ currentMember.name?.charAt(0) || 'V' }}</el-avatar>
                        <div class="overflow-hidden">
                            <div class="font-black text-lg text-gray-800 truncate">{{ currentMember.name }}</div>
                            <div class="text-xs text-gray-500 mt-1 flex items-center gap-2">
                                <span class="bg-blue-100 text-blue-700 px-2 py-0.5 rounded font-bold border border-blue-200">{{ memberLevelDesc }}</span>
                                <span class="font-mono">{{ currentMember.phone }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex justify-between items-center bg-white/60 p-2 rounded">
                        <span class="text-sm text-gray-600 font-bold">会员余额</span><span class="text-lg font-black text-blue-600">￥{{ (currentMember.balance || 0).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between items-center bg-white/60 p-2 rounded mt-2">
                        <span class="text-sm text-gray-600 font-bold">会员券 (抵扣)</span><span class="text-lg font-black text-teal-600">￥{{ (currentMember.coupon || 0).toFixed(2) }}</span>
                    </div>
                    <div class="flex justify-between items-center bg-white/60 p-2 rounded mt-2">
                        <span class="text-sm text-gray-600 font-bold">拥有满减券</span><span class="text-lg font-bold text-orange-500">{{ currentMember.voucherCount || 0 }} 张</span>
                    </div>
                </div>
                <div v-else class="bg-gray-50 p-4 rounded-lg border border-dashed flex flex-col items-center justify-center text-gray-400 h-[215px] shrink-0">
                    <el-icon :size="40" class="mb-2"><UserFilled /></el-icon><p class="tracking-widest font-bold">普通散客，无会员特权</p>
                </div>

                <div class="flex-1 bg-gray-50 p-4 rounded-lg border flex flex-col overflow-y-auto">
                    <div class="flex flex-col gap-2 mb-2 text-orange-600">
                        <div class="flex justify-between items-center">
                            <span class="font-bold flex items-center gap-1 whitespace-nowrap"><el-icon><Ticket /></el-icon> 满减券</span>
                            <el-select v-model="selectedCouponRule" placeholder="请选择使用券" class="w-[160px]" size="default" @change="handleCouponRuleChange" clearable value-key="ruleId">
                                <el-option v-for="c in availableCoupons" :key="c.ruleId" :label="c.name" :value="c" />
                            </el-select>
                        </div>
                        <div class="flex justify-between items-center mt-2" v-if="selectedCouponRule">
                            <span class="text-xs text-green-600 font-bold">最多可用 {{ maxUsableCoupons }} 张</span>
                            <div class="flex items-center gap-2">
                                <span class="text-sm text-gray-500 whitespace-nowrap">使用张数:</span>
                                <el-input-number v-model="usedCouponCount" :min="1" :max="maxUsableCoupons" :step="1" class="!w-[100px]" size="small" @change="recalculatePayments" />
                            </div>
                        </div>
                        <div v-if="currentMember.id" class="text-[11px] text-gray-400 text-right mt-1">
                            当前符合满减活动的总额: ￥{{ participatingAmount.toFixed(2) }}
                        </div>
                    </div>

                    <div class="mt-auto flex flex-col">
                        <div class="flex justify-between items-center text-blue-600 border-t pt-3 mb-1">
                            <span class="font-bold whitespace-nowrap">🏷️ 整单优惠:</span>
                            <el-input-number v-model="manualDiscount" :min="0" :max="totalAmount" :step="1" class="!w-[130px]" placeholder="直减金额" @change="recalculatePayments" />
                        </div>

                        <div class="flex flex-col border-t border-dashed pt-3 mt-1" v-if="currentMember.id && actualCouponUsed > 0">
                            <div class="flex justify-between items-center text-teal-600">
                                <span class="font-bold whitespace-nowrap flex items-center gap-1"><el-icon><PriceTag /></el-icon> 免收会员券:</span>
                                <el-switch v-model="isWaiveCoupon" active-text="是" inactive-text="否" inline-prompt @change="recalculatePayments" />
                            </div>
                            <div v-if="!isWaiveCoupon && (currentMember.coupon || 0) < actualCouponUsed" class="text-xs text-red-500 text-right mt-1 font-bold animate-pulse">⚠️ 当前会员券余额不足，请充值或开启免收！</div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="w-[58%] flex flex-col border rounded-lg overflow-hidden shadow-sm">
                <div class="bg-red-50 p-5 border-b border-red-100 flex justify-between items-center text-red-600 shrink-0">
                    <div>
                        <div class="text-2xl font-black">最终应收</div>
                        <div v-if="currentMember.id && actualCouponUsed > 0" class="text-sm font-bold text-teal-600 mt-1">(含扣减会员券: ￥{{ actualCouponUsed.toFixed(2) }})</div>
                    </div>
                    <span class="text-6xl font-black tracking-tighter">￥{{ finalPayAmount.toFixed(2) }}</span>
                </div>
                <div class="flex-1 bg-white p-4 overflow-y-auto">
                    <div class="text-gray-500 font-bold mb-3 flex justify-between items-center border-b pb-2">
                        <span>组合支付金额 (智能分摊)</span><span v-if="changeAmount > 0" class="text-green-500 text-sm flex items-center gap-1 font-bold"><el-icon><Money /></el-icon>包含找零</span>
                    </div>
                    <div class="flex flex-col gap-3">
                        <div v-for="(pay, index) in paymentList" :key="pay.code" class="flex items-center gap-3 bg-gray-50 p-2 rounded border focus-within:border-blue-400 focus-within:bg-blue-50 transition-colors">
                            <div class="w-24 font-bold text-gray-700 text-sm tracking-wider truncate" :title="pay.name">{{ pay.name }}</div>
                            <el-input-number :model-value="pay.amount" :min="0" :step="10" class="flex-1 !h-[45px] !text-2xl font-bold" :controls="false" placeholder="0" @update:model-value="(val) => handlePaymentChange(index, val)" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <template #footer>
            <div class="flex justify-between items-center px-4 py-3 bg-gray-100 rounded-b-lg border-t mt-2">
                <div class="text-left shrink-0">
                    <div class="text-gray-600 font-bold text-sm">实收总计: <span class="text-gray-900 font-black text-xl">￥{{ totalPaid.toFixed(2) }}</span></div>
                    <div v-if="changeAmount > 0" class="text-green-600 font-black text-xl mt-1">找零金额: ￥{{ changeAmount.toFixed(2) }}</div>
                    <div v-else-if="unpaidAmount > 0" class="text-red-500 font-black text-xl mt-1">还差金额: ￥{{ unpaidAmount.toFixed(2) }}</div>
                </div>
                <div class="flex gap-3 flex-1 justify-end ml-4">
                    <el-button size="large" class="w-28 font-bold text-base" @click="visible = false">取消(Esc)</el-button>
                    <el-button type="danger" size="large" class="!text-2xl font-black tracking-widest shadow-md px-8" @click="submitOrderAction" :loading="submitLoading" :disabled="unpaidAmount > 0 || (!isWaiveCoupon && currentMember.id && currentMember.coupon < actualCouponUsed)">确认收款</el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { UserFilled, Ticket, PriceTag, Money } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePosStore } from '../hooks/usePosStore'
// 🌟 彻底移除了无用的 SettleEngine，完全解耦！

const props = defineProps(['modelValue', 'payMethodDict', 'memberLevelDesc'])
const emit = defineEmits(['update:modelValue', 'checkout-success', 'closed'])

const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})

const submitLoading = ref(false)

// 🌟 引入核心计算器 getCartItemPrices
const {
    cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
    totalAmount, actualCouponUsed, finalPayAmount, submitOrder, getCartItemPrices
} = usePosStore();

// ==========================================
// 🌟 修复 2：前端同步后端的“满减池”算法
// ==========================================
const participatingAmount = computed(() => {
    return cartList.value.reduce((sum, item) => {
        // 只有商品标明了参与满减（isDiscountParticipable === 1）才计入
        if (item.isDiscountParticipable === 1) {
            const { unitPrice, unitCoupon } = getCartItemPrices(item, currentMember.value);
            // 计入口径：单品实付价(基准价 - 会员券) * 数量
            const finalPrice = unitPrice - unitCoupon;
            return sum + (finalPrice * item.quantity);
        }
        return sum;
    }, 0);
});

// 计算可用券，严格对比【participatingAmount】而不是总金额！
const availableCoupons = computed(() => {
    if (!currentMember.value.id || !Array.isArray(currentMember.value.couponList)) return []
    return currentMember.value.couponList.filter(c => participatingAmount.value >= c.threshold)
})

// 计算最多可用几张，严格按照【participatingAmount】计算！
const maxUsableCoupons = computed(() => {
    if (!currentMember.value.id || !selectedCouponRule.value) return 0;
    const maxByAmount = Math.floor(participatingAmount.value / selectedCouponRule.value.threshold);
    return Math.min(maxByAmount, selectedCouponRule.value.availableCount || 0);
})

const totalPaid = computed(() => paymentList.value.reduce((sum, item) => sum + (item.amount || 0), 0))
const unpaidAmount = computed(() => Math.max(0, finalPayAmount.value - totalPaid.value))

const changeAmount = computed(() => {
    const total = paymentList.value.reduce((sum, p) => sum + (p.amount || 0), 0);
    if (total <= finalPayAmount.value) return 0;
    const hasCash = paymentList.value.some(p => p.code.includes('CASH') && p.amount > 0);
    return hasCash ? total - finalPayAmount.value : 0;
})

watch(visible, (newVal) => {
    if (newVal) {
        usedCouponCount.value = maxUsableCoupons.value;
        const sourceDict = props.payMethodDict.length > 0 ? props.payMethodDict : [{value: 'AGGREGATE', desc: '聚合扫码'}, {value: 'CASH', desc: '现金支付'}]
        paymentList.value = sourceDict.map(dict => ({ code: dict.value, name: dict.desc, amount: 0 }))
        recalculatePayments();
    }
})

const handleCouponRuleChange = () => { usedCouponCount.value = selectedCouponRule.value ? maxUsableCoupons.value : 0; recalculatePayments(); }

const recalculatePayments = () => {
    if (paymentList.value.length === 0) return;
    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    const targetIndex = aggIndex >= 0 ? aggIndex : 0;
    paymentList.value.forEach((p, i) => { if (i !== targetIndex) p.amount = 0; });
    paymentList.value[targetIndex].amount = finalPayAmount.value;
}

const handlePaymentChange = (index, val) => {
    let newVal = val || 0;
    if (paymentList.value[index].code.includes('BALANCE')) {
        const maxBal = currentMember.value.balance || 0;
        if (newVal > maxBal) { newVal = maxBal; ElMessage.warning('已限制为最大可用本金余额！'); }
    }
    paymentList.value[index].amount = newVal;
    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    if (aggIndex !== -1 && aggIndex !== index) {
        let otherSum = paymentList.value.reduce((sum, p, i) => i !== aggIndex ? sum + p.amount : sum, 0);
        paymentList.value[aggIndex].amount = parseFloat(Math.max(0, finalPayAmount.value - otherSum).toFixed(2));
    }
}

const submitOrderAction = async () => {
    if (unpaidAmount.value > 0) return ElMessage.error(`实付不足 ￥${unpaidAmount.value.toFixed(2)}`);
    const validPayments = paymentList.value.filter(p => p.amount > 0).map(p => ({ payMethodCode: p.code, payMethodName: p.name, payAmount: p.amount }));

    // 🌟 后端现在只认 goodsId 和 quantity，前面的 goodsPrice 是盲传，直接传最终单品实付价就行了
    const orderDetails = cartList.value.map(item => {
        const { unitPrice, unitCoupon } = getCartItemPrices(item, currentMember.value);
        return {
            goodsId: item.id,
            quantity: item.quantity,
            goodsPrice: unitPrice - unitCoupon
        };
    });

    submitLoading.value = true
    try {
        await submitOrder({
            member: currentMember.value.id || null,
            usedCouponRuleId: selectedCouponRule.value?.ruleId,
            usedCouponCount: usedCouponCount.value,
            waiveCoupon: isWaiveCoupon.value,
            manualDiscountAmount: manualDiscount.value || 0,
            orderDetail: orderDetails,
            payments: validPayments
        })
        ElMessage.success('收款成功！订单已真实入库！')
        emit('checkout-success', { total: finalPayAmount.value, paid: totalPaid.value, change: changeAmount.value })
        visible.value = false;
    } catch (error) {
        ElMessage.error(error.message || '结账失败，后端计算拦截')
    } finally {
        submitLoading.value = false
    }
}
</script>

<style scoped>
:deep(.checkout-dialog .el-dialog__header) { display: none; }
:deep(.checkout-dialog .el-dialog__body) { padding: 0; border-radius: 8px; overflow: hidden; }
</style>