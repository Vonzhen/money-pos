<template>
    <el-dialog v-model="visible" title="会员业务 - 前台办理" width="550px" @open="initData" @closed="$emit('closed')" destroy-on-close>

        <div class="mb-4">
            <el-select v-model="form.memberId" filterable remote reserve-keyword placeholder="🔍 输入手机号 / 姓名拼音搜索并选择会员" :remote-method="querySearch" :loading="loading" class="w-full" size="large" @change="handleSelect">
                <template #prefix><el-icon class="text-gray-400 text-lg"><Search /></el-icon></template>
                <el-option v-for="item in options" :key="item.id" :label="`${item.name} (${item.phone})`" :value="item.id">
                    <div class="flex justify-between items-center w-full">
                        <span class="font-bold text-gray-800">{{ item.name }}</span>
                        <span class="text-gray-400 text-sm">{{ item.phone }}</span>
                    </div>
                </el-option>
            </el-select>
        </div>

        <div v-if="form.memberId" class="mb-5">
            <div class="text-sm text-gray-600 bg-blue-50/50 p-3 rounded-md border border-blue-100 flex flex-col gap-2">
                <div class="flex justify-between items-center pb-2 border-b border-blue-200/50">
                    <span class="text-gray-500">办理对象: <b class="text-blue-600 text-base ml-1 tracking-wider">{{ form.memberName }}</b></span>
                    <span class="text-gray-500">手机号码: <b class="text-gray-800 font-mono ml-1">{{ form.memberPhone }}</b></span>
                </div>
                <div class="flex justify-between items-center pt-1">
                    <span class="text-gray-500">会员余额: <b class="text-gray-800 ml-1">￥{{ (form.currentBalance || 0).toFixed(2) }}</b></span>
                    <span class="text-gray-500">会员券: <b class="text-gray-800 ml-1">￥{{ (form.currentCoupon || 0).toFixed(2) }}</b></span>
                    <span class="text-gray-500">满减券: <b class="text-gray-800 ml-1">{{ form.currentVoucherCount || 0 }} 张</b></span>
                </div>
            </div>
        </div>

        <div v-if="form.memberId">
            <el-form :model="form" label-width="105px" label-position="left">
                <el-form-item label="业务类型">
                    <el-radio-group v-model="form.type" class="w-full flex">
                        <el-radio-button value="BALANCE" class="flex-1 text-center">充值余额</el-radio-button>
                        <el-radio-button value="COUPON" class="flex-1 text-center">充值会员券</el-radio-button>
                        <el-radio-button value="VOUCHER" class="flex-1 text-center">发满减券</el-radio-button>
                    </el-radio-group>
                </el-form-item>

                <template v-if="form.type === 'BALANCE'">
                    <el-form-item label="充值余额" required><el-input-number v-model="form.amount" :min="0" :step="100" class="!w-full" :controls="false" placeholder="输入充值金额" /></el-form-item>
                    <el-form-item label="赠送会员券"><el-input-number v-model="form.giftCoupon" :min="0" :step="50" class="!w-full" :controls="false" placeholder="选填，输入赠送金额" /></el-form-item>
                </template>

                <template v-if="form.type === 'COUPON'">
                    <el-form-item label="充值会员券" required>
                        <el-input-number v-model="form.amount" :min="0" :step="100" class="!w-full" :controls="false" placeholder="输入系统增加的券额 (如：1000)" />
                    </el-form-item>
                    <el-form-item label="实收金额" required>
                        <el-input-number v-model="form.realAmount" :min="0" :step="10" class="!w-full" :controls="false" placeholder="输入顾客实际支付的现金 (如：100)" />
                    </el-form-item>
                </template>

                <template v-if="form.type === 'VOUCHER'">
                    <el-form-item label="选择满减券" required>
                        <el-select v-model="form.ruleId" class="w-full" placeholder="请选择要发放的满减规则">
                            <el-option v-for="rule in allCouponRulesList" :key="rule.id" :label="`满${rule.thresholdAmount}减${rule.discountAmount}`" :value="rule.id" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="发放张数" required><el-input-number v-model="form.quantity" :min="1" :step="1" class="!w-full" /></el-form-item>
                </template>

                <el-form-item label="备注信息">
                    <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，可输入充值或发券备注（如：店庆活动赠送）" />
                </el-form-item>
            </el-form>
        </div>

        <template #footer>
            <el-button @click="visible = false" size="large">取消</el-button>
            <el-button type="primary" class="w-32 font-bold" size="large" :disabled="!form.memberId || (form.type === 'COUPON' && (form.amount == null || form.realAmount == null))" @click="submit" :loading="submitLoading">确定办理</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import memberApi from "@/api/ums/member.js"
import { usePosStore } from '../../hooks/usePosStore'

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const { currentMember } = usePosStore()
const options = ref([]); const loading = ref(false); const submitLoading = ref(false)
const allCouponRulesList = ref([])

const form = ref({
    memberId: null, memberName: '', memberPhone: '',
    currentBalance: 0, currentCoupon: 0, currentVoucherCount: 0,
    type: 'BALANCE', amount: undefined, giftCoupon: undefined, realAmount: undefined, // 🌟 增加 realAmount
    ruleId: null, quantity: 1, remark: ''
})

const initData = async () => {
    form.value = {
        memberId: null, memberName: '', memberPhone: '',
        currentBalance: 0, currentCoupon: 0, currentVoucherCount: 0,
        type: 'BALANCE', amount: undefined, giftCoupon: undefined, realAmount: undefined,
        ruleId: null, quantity: 1, remark: ''
    }
    options.value = []
    try {
        const res = await memberApi.getCouponRules();
        allCouponRulesList.value = res.data || res || []
    } catch (e) { }
}

const querySearch = async (query) => {
    if (!query) { options.value = []; return }
    loading.value = true
    try {
        const res = await memberApi.posSearch(query);
        options.value = res.data || []
    } catch (e) {
        options.value = []
    } finally {
        loading.value = false
    }
}

const handleSelect = (val) => {
    if (!val) return
    const item = options.value.find(i => i.id === val)
    if (!item) return

    form.value.memberId = item.id;
    form.value.memberName = item.name;
    form.value.memberPhone = item.phone;
    form.value.currentBalance = item.balance || 0;
    form.value.currentCoupon = item.coupon || 0;
    form.value.currentVoucherCount = item.voucherCount || item.couponCount || 0;

    options.value = [item]
}

const submit = async () => {
    submitLoading.value = true
    try {
        await memberApi.recharge({
            memberId: form.value.memberId,
            type: form.value.type,
            amount: form.value.amount || 0,
            realAmount: form.value.type === 'COUPON' ? (form.value.realAmount || 0) : undefined, // 🌟 传入实收金额
            giftCoupon: form.value.giftCoupon || 0,
            ruleId: form.value.ruleId,
            quantity: form.value.quantity || 0,
            remark: form.value.remark
        })
        ElMessage.success(`操作成功！`)

        if (currentMember.value.id === form.value.memberId) {
            if (form.value.type === 'BALANCE') {
                currentMember.value.balance += form.value.amount;
                currentMember.value.coupon += (form.value.giftCoupon || 0)
            }
            else if (form.value.type === 'COUPON') {
                currentMember.value.coupon += form.value.amount
            }
            else if (form.value.type === 'VOUCHER') {
                currentMember.value.voucherCount = (currentMember.value.voucherCount || 0) + form.value.quantity;
            }
        }
        visible.value = false
    } catch(e) {
        ElMessage.error('办理失败，请重试')
    } finally {
        submitLoading.value = false
    }
}
</script>

<style scoped>
:deep(.el-input-number .el-input__inner) { text-align: left; padding-left: 15px; }
</style>