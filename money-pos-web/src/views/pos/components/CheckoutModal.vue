<template>
    <el-dialog v-model="visible" title="混合收银工作站" width="900px" top="6vh" destroy-on-close class="checkout-dialog" @closed="handleClosed">
        <div class="flex gap-4 p-4 pb-0 h-[480px]">
            <div class="w-[42%] flex flex-col gap-2">
                <div v-if="currentMember.id" class="bg-gradient-to-br from-blue-50 to-indigo-50 p-3 rounded-lg border border-blue-200 shadow-inner shrink-0">
                    <div class="flex items-center gap-3 mb-2 border-b border-blue-100 pb-2">
                        <el-avatar :size="40" class="bg-blue-500 font-bold">{{ currentMember.name?.charAt(0) || 'V' }}</el-avatar>
                        <div class="overflow-hidden">
                            <div class="font-black text-base text-gray-800 truncate">{{ currentMember.name }}</div>
                            <div class="text-xs text-gray-500 mt-0.5 flex items-center gap-2">
                                <span class="bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded font-bold border border-blue-200">{{ memberLevelDesc || '会员' }}</span>
                                <span class="font-mono">{{ currentMember.phone }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="space-y-1.5">
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">会员余额</span><span class="text-base font-black text-blue-600">￥{{ (currentMember.balance || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">会员券 (抵扣)</span><span class="text-base font-black text-teal-600">￥{{ (currentMember.coupon || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">拥有满减券</span><span class="text-base font-bold text-orange-500">{{ currentMember.voucherCount || 0 }} 张</span>
                        </div>
                    </div>
                </div>
                <div v-else class="bg-gray-50 p-4 rounded-lg border border-dashed flex flex-col items-center justify-center text-gray-400 h-[180px] shrink-0">
                    <el-icon :size="40" class="mb-2"><UserFilled /></el-icon><p class="tracking-widest font-bold">普通散客，无会员特权</p>
                </div>

                <div class="flex-1 bg-gray-50 p-3.5 rounded-lg border flex flex-col justify-between">
                    <div class="flex flex-col gap-2 text-orange-600">
                        <div class="flex justify-between items-center">
                            <span class="font-bold flex items-center gap-1 whitespace-nowrap"><el-icon><Ticket /></el-icon> 满减券</span>
                            <el-select v-model="selectedCouponRule" placeholder="请选择使用券" class="w-[160px]" size="default" @change="handleCouponRuleChange" clearable>
                                <el-option v-for="c in availableCoupons" :key="c.ruleId" :label="c.name" :value="c" />
                            </el-select>
                        </div>
                        <div class="flex justify-between items-center mt-1 min-h-[32px]">
                            <template v-if="selectedCouponRule">
                                <span class="text-xs text-green-600 font-bold">最多可用 {{ maxUsableCoupons }} 张</span>
                                <div class="flex items-center gap-2">
                                    <span class="text-xs text-gray-500 whitespace-nowrap">使用:</span>
                                    <el-input-number v-model="usedCouponCount" :min="1" :max="maxUsableCoupons" :step="1" class="!w-[90px]" size="small" @change="handleDiscountChange" />
                                </div>
                            </template>
                        </div>
                        <div v-if="currentMember.id" class="text-[10px] text-gray-400 text-right">
                            当前符合活动金额: ￥{{ participatingAmount.toFixed(2) }}
                        </div>
                    </div>

                    <div class="flex flex-col mt-2">
                        <div class="flex justify-between items-center text-blue-600 border-t border-gray-200 pt-3">
                            <span class="font-bold whitespace-nowrap">🏷️ 整单优惠:</span>
                            <el-input-number v-model="manualDiscount" :min="0" :max="totalAmount" :precision="2" :step="1" class="!w-[130px]" placeholder="直减" @change="handleDiscountChange" />
                        </div>

                        <div class="flex flex-col border-t border-dashed border-gray-300 pt-3 mt-3 min-h-[45px]" v-show="currentMember.id && theoreticalCouponUsed > 0">
                            <div class="flex justify-between items-center text-teal-600">
                                <span class="font-bold whitespace-nowrap flex items-center gap-1"><el-icon><PriceTag /></el-icon> 免收会员券:</span>
                                <el-switch v-model="isWaiveCoupon" active-text="是" inactive-text="否" inline-prompt @change="handleDiscountChange" />
                            </div>
                            <div v-if="!isWaiveCoupon && (currentMember.coupon || 0) < theoreticalCouponUsed" class="text-[10px] text-red-500 text-right mt-1.5 font-bold animate-pulse leading-tight">
                                ⚠️ 余额不足以抵扣 ￥{{ theoreticalCouponUsed.toFixed(2) }}，请充值或开启免收！
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="w-[65%] flex flex-col border rounded-lg overflow-hidden shadow-sm">
                <div class="bg-red-50 p-4 lg:p-5 border-b border-red-100 flex justify-between items-center text-red-600 shrink-0">
                    <div class="flex flex-col justify-center shrink-0 mr-2">
                        <div class="text-2xl font-black whitespace-nowrap">最终应收</div>
                        <div v-if="currentMember.id && theoreticalCouponUsed > 0 && !isWaiveCoupon" class="text-xs font-bold text-teal-600 mt-1 whitespace-nowrap">
                            (抵扣会员券: ￥{{ theoreticalCouponUsed.toFixed(2) }})
                        </div>
                        <div v-else-if="currentMember.id && theoreticalCouponUsed > 0 && isWaiveCoupon" class="text-xs font-bold text-gray-400 line-through mt-1 whitespace-nowrap">
                            (免扣会员券: ￥{{ theoreticalCouponUsed.toFixed(2) }})
                        </div>
                    </div>
                    <div class="text-right flex-1 flex justify-end items-center overflow-hidden">
                        <span class="font-black tracking-tighter" :class="finalPayAmount >= 100000 ? 'text-4xl' : (finalPayAmount >= 10000 ? 'text-5xl' : 'text-6xl')">
                            ￥{{ finalPayAmount.toFixed(2) }}
                        </span>
                    </div>
                </div>
                <div class="flex-1 bg-white p-4 overflow-y-auto">
                    <div class="text-gray-500 font-bold mb-3 flex justify-between items-center border-b pb-2">
                        <span>组合支付明细</span><span v-if="changeAmount > 0" class="text-green-500 text-sm flex items-center gap-1 font-bold">包含找零</span>
                    </div>

                    <div class="grid gap-3" :class="paymentList.length > 3 ? 'grid-cols-2' : 'grid-cols-1'">
                        <div v-for="(pay, index) in paymentList" :key="pay.code" class="flex flex-col bg-gray-50 p-2 rounded border focus-within:border-blue-400 focus-within:bg-blue-50 transition-colors">
                            <div class="flex items-center gap-3">
                                <div class="w-20 font-bold text-gray-700 text-sm tracking-wider truncate" :title="pay.name">{{ pay.name }}</div>
                                <el-input-number
                                    :model-value="pay.amount"
                                    :min="0"
                                    :max="pay.code.includes('CASH') ? 999999 : (Number(finalPayAmount) || 0)"
                                    :precision="2"
                                    :step="10"
                                    class="flex-1 !h-[45px] !text-2xl font-bold"
                                    :controls="false"
                                    placeholder="0"
                                    @update:model-value="(val) => handlePaymentChange(index, val)"
                                />
                            </div>
                            <div v-if="pay.code === 'AGGREGATE' && payTagDict && payTagDict.length > 0" class="flex flex-wrap gap-2 mt-2 ml-[5.5rem]">
                                <el-tag v-for="tag in payTagDict" :key="tag.value" :type="pay.activeTag === tag.value ? 'success' : 'info'" :effect="pay.activeTag === tag.value ? 'dark' : 'plain'" class="cursor-pointer font-bold border-0 shadow-sm transition-all hover:scale-105" @click="pay.activeTag = tag.value">
                                    {{ tag.desc }}
                                </el-tag>
                            </div>
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
                    <el-button type="danger" size="large" class="!text-2xl font-black tracking-widest shadow-md px-8" @click="submitOrderAction" :loading="submitLoading" :disabled="unpaidAmount > 0 || (!isWaiveCoupon && currentMember.id && currentMember.coupon < theoreticalCouponUsed)">确认收款</el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { UserFilled, Ticket, PriceTag } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePosStore } from '../hooks/usePosStore'

const props = defineProps({
    modelValue: Boolean,
    payMethodDict: { type: Array, default: () => [] },
    payTagDict: { type: Array, default: () => [{value: 'WECHAT', desc: '微信支付'}, {value: 'ALIPAY', desc: '支付宝'}] },
    memberLevelDesc: String
})
const emit = defineEmits(['update:modelValue', 'checkout-success', 'closed'])

const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})

const submitLoading = ref(false)

// 🌟 核心：从 Store 全量引入真正的后端真理！删掉组件自己算的逻辑！
const {
    cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
    totalAmount, actualCouponUsed, finalPayAmount, theoreticalCouponUsed, participatingAmount,
    reqId, prepareCheckout, clearAll, submitOrder, runTrial
} = usePosStore();

const availableCoupons = computed(() => {
    if (!currentMember.value.id || !Array.isArray(currentMember.value.couponList)) return []
    return currentMember.value.couponList.filter(c => participatingAmount.value >= c.threshold)
})

const maxUsableCoupons = computed(() => {
    if (!currentMember.value.id || !selectedCouponRule.value) return 0;
    const maxByAmount = Math.floor(participatingAmount.value / selectedCouponRule.value.threshold);
    return Math.min(maxByAmount, selectedCouponRule.value.availableCount || 0);
})

const totalPaid = computed(() => paymentList.value.reduce((sum, item) => sum + (item.amount || 0), 0))
const unpaidAmount = computed(() => {
    const pay = Number(finalPayAmount.value) || 0;
    const paid = Number(totalPaid.value) || 0;
    return Math.max(0, pay - paid);
})

// 🌟 财务合规的金融级找零：只允许现金溢出产生找零！
const changeAmount = computed(() => {
    const cashItem = paymentList.value.find(p => p.code.includes('CASH'));
    const cashPaid = cashItem ? (Number(cashItem.amount) || 0) : 0;
    const nonCashPaid = totalPaid.value - cashPaid;
    const pay = Number(finalPayAmount.value) || 0;
    const remainToPay = Math.max(0, pay - nonCashPaid);
    return cashPaid > remainToPay ? Number((cashPaid - remainToPay).toFixed(2)) : 0;
})

watch(visible, (newVal) => {
    if (newVal) {
        prepareCheckout();
        usedCouponCount.value = 0;
        const sourceDict = props.payMethodDict.length > 0 ? props.payMethodDict : [{value: 'AGGREGATE', desc: '聚合扫码'}, {value: 'CASH', desc: '现金支付'}]

        paymentList.value = sourceDict.map(dict => ({
            code: dict.value,
            name: dict.desc,
            amount: 0,
            activeTag: (dict.value === 'AGGREGATE' && props.payTagDict.length > 0) ? props.payTagDict[0].value : null
        }))
        recalculatePayments();
    }
})

// 🌟 每次改动折扣或券，都要呼叫后端重新试算，然后重新分摊钱！
const handleDiscountChange = () => {
    runTrial();
    // 加一点点延迟等后端结果，体验更好
    setTimeout(() => { recalculatePayments(); }, 350);
}

const handleCouponRuleChange = () => {
    usedCouponCount.value = selectedCouponRule.value ? maxUsableCoupons.value : 0;
    handleDiscountChange();
}

const recalculatePayments = () => {
    if (paymentList.value.length === 0) return;
    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    const targetIndex = aggIndex >= 0 ? aggIndex : 0;
    paymentList.value.forEach((p, i) => { if (i !== targetIndex) p.amount = 0; });

    const safePayAmount = Number(finalPayAmount.value) || 0;
    paymentList.value[targetIndex].amount = Number(safePayAmount.toFixed(2));
}

const handlePaymentChange = (index, val) => {
    let newVal = Number((val || 0).toFixed(2));

    if (paymentList.value[index].code.includes('BALANCE')) {
        const maxBal = currentMember.value.balance || 0;
        if (newVal > maxBal) { newVal = maxBal; ElMessage.warning('已限制为最大可用会员余额！'); }
    }

    paymentList.value[index].amount = newVal;

    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    if (aggIndex !== -1 && aggIndex !== index) {
        let otherSum = paymentList.value.reduce((sum, p, i) => i !== aggIndex ? sum + p.amount : sum, 0);
        const safePayAmount = Number(finalPayAmount.value) || 0;
        paymentList.value[aggIndex].amount = Number(Math.max(0, safePayAmount - otherSum).toFixed(2));
    }
}

const handleClosed = () => {
    paymentList.value = [];
    manualDiscount.value = 0;
    isWaiveCoupon.value = false;
    selectedCouponRule.value = null;
    runTrial();
    emit('closed');
}

const submitOrderAction = async () => {
    if (unpaidAmount.value > 0) return ElMessage.error(`实付不足 ￥${unpaidAmount.value.toFixed(2)}`);

    const validPayments = paymentList.value
        .filter(p => p.amount > 0)
        .map(p => ({
            payMethodCode: p.code,
            payMethodName: p.name,
            payAmount: p.amount,
            payTag: p.activeTag || null
        }));

    // 🌟 纯净版 DTO：把价格字段摘除，后端绝对集权
    const orderDetails = cartList.value.map(item => ({
        goodsId: item.id,
        quantity: Number(item.qty) || 1
    }));

    submitLoading.value = true
    try {
        const payload = {
            reqId: reqId.value, // 🌟 补齐幂等神键
            member: currentMember.value.id || null,
            usedCouponRuleId: selectedCouponRule.value?.ruleId || null,
            usedCouponCount: selectedCouponRule.value ? usedCouponCount.value : 0,
            waiveCoupon: isWaiveCoupon.value,
            manualDiscountAmount: manualDiscount.value || 0,
            orderDetail: orderDetails,
            payments: validPayments
        };

        await submitOrder(payload)
        ElMessage.success('收款成功！订单已真实入库！')

        emit('checkout-success', {
            total: totalAmount.value,
            paid: totalPaid.value,
            couponUsed: actualCouponUsed.value
        })
        visible.value = false;
        // 注意：清空交由上层，或在 closed 钩子，不在这里调 clearAll 避免动画闪烁
    } catch (error) {
        console.error("结账异常", error);
        ElMessage.error(error.msg || error.message || '结账失败，后端风控拦截');
    } finally {
        submitLoading.value = false
    }
}
</script>

<style scoped>
:deep(.checkout-dialog .el-dialog__header) { display: none; }
:deep(.checkout-dialog .el-dialog__body) { padding: 0; border-radius: 8px; overflow: hidden; }
</style>