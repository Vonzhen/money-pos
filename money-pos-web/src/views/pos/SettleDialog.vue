<template>
    <el-dialog
        v-model="visible"
        title="结算清单"
        :width="dialogWidth"
        :close-on-click-modal="false"
        class="settle-dialog"
        @close="emit('update:modelValue', false)"
    >
        <div class="space-y-3">
            <div :class="showTwoColumns ? 'settle-grid' : ''">
                <template v-if="showTwoColumns">
                    <div class="settle-column" v-for="(column, colIndex) in settleColumns" :key="colIndex">
                        <div class="grid grid-cols-4 text-center text-sm font-medium el-text--secondary pb-2 border-b border-border">
                            <span>商品</span><span>原价</span><span>现价</span><span>优惠</span>
                        </div>
                        <div v-for="(item, index) in column" :key="index" class="py-2 border-b border-border last:border-0">
                            <div class="grid grid-cols-4 text-center text-sm">
                                <span class="font-medium">{{ item.goodsName }}</span>
                                <span class="el-text--secondary">{{ item.salePrice }}</span>
                                <span class="text-green-600 font-medium">{{ item.goodsPrice }}</span>
                                <span class="text-blue-500">{{ isVip ? item.coupon : 0 }}</span>
                            </div>
                            <div class="grid grid-cols-3 text-center text-xs el-text--secondary mt-1">
                                <span>× {{ item.quantity }}</span>
                                <span>小计 {{ NP.times(item.goodsPrice, item.quantity) }}</span>
                                <span>优惠 {{ isVip ? NP.times(item.coupon, item.quantity) : 0 }}</span>
                            </div>
                        </div>
                    </div>
                </template>
                <template v-else>
                    <div class="grid grid-cols-4 text-center text-sm font-medium el-text--secondary pb-2 border-b border-border">
                        <span>商品</span><span>原价</span><span>现价</span><span>优惠</span>
                    </div>
                    <div v-for="(item, index) in order" :key="index" class="py-2 border-b border-border last:border-0">
                        <div class="grid grid-cols-4 text-center text-sm">
                            <span class="font-medium">{{ item.goodsName }}</span>
                            <span class="el-text--secondary">{{ item.salePrice }}</span>
                            <span class="text-green-600 font-medium">{{ item.goodsPrice }}</span>
                            <span class="text-blue-500">{{ isVip ? item.coupon : 0 }}</span>
                        </div>
                        <div class="grid grid-cols-3 text-center text-xs el-text--secondary mt-1">
                            <span>× {{ item.quantity }}</span>
                            <span>小计 {{ NP.times(item.goodsPrice, item.quantity) }}</span>
                            <span>优惠 {{ isVip ? NP.times(item.coupon, item.quantity) : 0 }}</span>
                        </div>
                    </div>
                </template>
            </div>

            <div class="bg-fill-light rounded-lg p-3 space-y-2">
                <div class="flex justify-between text-sm">
                    <span class="el-text--secondary">总计(原价)</span>
                    <span class="font-medium">{{ totalAmount }}</span>
                </div>
                <div class="flex justify-between text-sm" v-if="NP.minus(totalAmount, payAmount) > 0">
                    <span class="el-text--secondary">会员专享优惠</span>
                    <span class="font-medium text-red-500">-{{ NP.minus(totalAmount, payAmount) }}</span>
                </div>
                <div class="flex justify-between text-sm" v-if="voucherDiscount > 0">
                    <span class="el-text--secondary">满减券抵扣</span>
                    <span class="font-medium text-red-500">-{{ voucherDiscount }}</span>
                </div>

                <div class="flex justify-between text-base font-bold mt-2 pt-2 border-t border-border">
                    <span>最终应付</span>
                    <span class="text-green-600 text-lg">¥ {{ finalPayAmount }}</span>
                </div>
            </div>

            <div class="mt-4 border border-blue-100 bg-blue-50/30 rounded-lg p-4">
                <div class="flex items-center justify-between mb-4">
                    <span class="font-bold text-gray-800 flex items-center gap-1">
                        <el-icon><Money /></el-icon> 收银台智能支付
                    </span>
                    <div>
                        <el-tag v-if="remainingAmount === 0" type="success" effect="dark">金额已结清</el-tag>
                        <el-tag v-else-if="remainingAmount < 0" type="warning" effect="dark">找零: ¥ {{ Math.abs(remainingAmount) }}</el-tag>
                        <el-tag v-else type="danger" effect="dark">还差: ¥ {{ remainingAmount }}</el-tag>
                    </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div v-for="pm in paymentMethods" :key="pm.value" class="flex items-center">
                        <span class="w-24 text-sm text-right pr-3 font-medium el-text--secondary leading-tight">
                            {{ pm.desc }}
                            <div v-if="pm.value === 'BALANCE'" class="text-xs text-blue-500 mt-1">(余:{{ member?.balance || 0 }})</div>
                        </span>
                        <el-input-number
                            v-model="payments[pm.value]"
                            :min="0"
                            :precision="2"
                            :max="pm.value === 'BALANCE' ? (member?.balance || 0) : 999999"
                            :controls="false"
                            class="flex-1 !w-full"
                            placeholder="0.00"
                            @change="handlePaymentInput(pm.value)"
                        />
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div class="flex justify-end gap-2">
                <el-button @click="handleCancel">取消</el-button>
                <el-button type="primary" :disabled="remainingAmount > 0" @click="handleConfirm">确认收款</el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import NP from 'number-precision'
import { isMobile } from '@/utils/index.js'
import { ElMessage } from 'element-plus'
import { Money } from '@element-plus/icons-vue'
import dictApi from "@/api/system/dict.js"

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    order: { type: Array, default: () => [] },
    isVip: { type: Boolean, default: false },
    member: { type: Object, default: null },
    voucherDiscount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const visible = ref(props.modelValue)

const totalAmount = computed(() => props.order.reduce((prev, next) => NP.plus(prev, NP.times(next.quantity, next.salePrice)), 0))
const couponAmount = computed(() => props.order.reduce((prev, next) => NP.plus(prev, NP.times(next.quantity, props.isVip ? next.coupon : 0)), 0))
const payAmount = computed(() => props.order.reduce((prev, next) => NP.plus(prev, NP.times(next.quantity, next.goodsPrice)), 0))

const finalPayAmount = computed(() => {
    const raw = NP.minus(payAmount.value, props.voucherDiscount);
    return raw > 0 ? raw : 0;
})

const paymentMethods = ref([])
const payments = ref({})

const loadDict = async () => {
    if (paymentMethods.value.length === 0) {
        const res = await dictApi.loadDict(["pos_payment_method"])
        paymentMethods.value = res.pos_payment_method || []
    }
}

watch(() => props.modelValue, async (val) => {
    visible.value = val
    if (val) {
        await loadDict()
        payments.value = {}
        paymentMethods.value.forEach(pm => { payments.value[pm.value] = 0 })

        // 初次打开时：优先扣余额，缺口扔给聚合扫码
        if (finalPayAmount.value > 0) {
            let remaining = finalPayAmount.value;

            if (props.member && props.member.balance > 0) {
                const useBalance = Math.min(props.member.balance, remaining);
                payments.value['BALANCE'] = useBalance;
                remaining = NP.minus(remaining, useBalance);
            }

            if (remaining > 0) {
                const agg = paymentMethods.value.find(p => p.value === 'AGGREGATE')
                if (agg) {
                    payments.value['AGGREGATE'] = remaining;
                } else if (paymentMethods.value.length > 0) {
                    const first = paymentMethods.value.find(p => p.value !== 'BALANCE')
                    if (first) { payments.value[first.value] = remaining; }
                }
            }
        }
    }
})

// ================= 🌟 跷跷板智能补齐引擎 =================
function handlePaymentInput(changedKey) {
    // 1. 确定谁来当“接盘侠”。优先级：聚合(AGGREGATE) > 现金(CASH) > 银联(UNIONPAY)
    let fallbacks = ['AGGREGATE', 'CASH', 'UNIONPAY'].filter(k => paymentMethods.value.some(p => p.value === k))

    // 如果客户配了其他的自定义名称，也塞进接盘侠池子里
    paymentMethods.value.forEach(pm => {
        if (pm.value !== 'BALANCE' && !fallbacks.includes(pm.value)) {
            fallbacks.push(pm.value)
        }
    })

    // 2. 找到排在第一位且【不是你刚刚修改的那个框】作为补齐目标
    let targetKey = fallbacks.find(k => k !== changedKey)
    if (!targetKey) return // 如果没有其他可填的框，系统就不动

    // 3. 算出除了这个“目标框”以外，其他所有框你已经填了多少钱
    let sumOthers = 0
    for (const k in payments.value) {
        if (k !== targetKey) {
            sumOthers = NP.plus(sumOthers, Number(payments.value[k]) || 0)
        }
    }

    // 4. 计算剩下的缺口，全部丢给“目标框”！
    let remainder = NP.minus(finalPayAmount.value, sumOthers)
    payments.value[targetKey] = remainder > 0 ? remainder : 0
}
// =======================================================

const totalPaid = computed(() => {
    let sum = 0;
    for (const key in payments.value) { sum = NP.plus(sum, Number(payments.value[key]) || 0); }
    return sum;
})
const remainingAmount = computed(() => NP.minus(finalPayAmount.value, totalPaid.value))

const mobile = isMobile()
const showTwoColumns = computed(() => !mobile && props.order.length > 6)
const dialogWidth = computed(() => {
    if (mobile) return '90%'
    return showTwoColumns.value ? '720px' : '480px'
})

const settleColumns = computed(() => {
    if (props.order.length <= 6) return [props.order]
    const mid = Math.ceil(props.order.length / 2)
    return [props.order.slice(0, mid), props.order.slice(mid)]
})

function handleCancel() {
    visible.value = false
    emit('update:modelValue', false)
    emit('cancel')
}

function handleConfirm() {
    if (remainingAmount.value > 0) {
        ElMessage.warning('支付金额未结清！')
        return
    }
    const actualPayments = Object.entries(payments.value)
        .filter(([k, v]) => v > 0)
        .map(([k, v]) => {
            const pm = paymentMethods.value.find(p => p.value === k)
            return {
                payMethodCode: k,
                payMethodName: pm ? pm.desc : k,
                payAmount: v
            }
        })
    emit('confirm', actualPayments)
}
</script>

<style lang="less" scoped>
.settle-dialog {
    :deep(.bg-fill-light) { background-color: var(--el-fill-color-light); }
    :deep(.border-border) { border-color: var(--el-border-color); }
}
.settle-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
.settle-column { min-width: 0; }
</style>