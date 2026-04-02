<template>
    <el-dialog v-model="visible" title="收银交接班与对账" width="600px" top="8vh" destroy-on-close @open="initShift" @closed="$emit('closed')">
        <div v-loading="loading" class="flex flex-col gap-4">

            <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 flex flex-col gap-3 shadow-sm shrink-0">
                <div class="flex justify-between items-center text-gray-800 font-bold text-lg border-b border-gray-200 pb-2">
                    <span class="flex items-center gap-2"><el-icon class="text-blue-500"><Avatar /></el-icon> 当班收银员：{{ cashierName || '未知收银员' }}</span>
                </div>
                <div class="flex items-center gap-2 mt-1">
                    <span class="text-sm text-gray-600 font-bold w-[70px]">接班时间:</span>
                    <el-date-picker v-model="startTime" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" :clearable="false" class="!w-[220px]" size="default" @change="fetchData" />
                </div>
                <div class="flex items-center gap-2">
                    <span class="text-sm text-gray-600 font-bold w-[70px]">交班时间:</span>
                    <span class="font-mono text-gray-800 bg-white px-2 py-1 rounded border shadow-inner">{{ shiftData?.shiftEndTime || '核算中...' }}</span>
                </div>
            </div>

            <div v-if="shiftData" class="border rounded-lg overflow-hidden shadow-sm flex flex-col max-h-[480px]">

                <div class="bg-red-50 p-4 border-b border-red-100 flex justify-between items-center shrink-0">
                    <div class="flex flex-col">
                        <span class="font-black text-red-700 text-lg">本班应缴营业额</span>
                        <span class="text-xs text-red-500 mt-1 font-bold">(仅含现金与扫码实收，应存现额)</span>
                    </div>
                    <span class="text-4xl font-black text-red-600 tracking-tighter drop-shadow-sm">￥{{ (shiftData.expectedTotalIncome || 0).toFixed(2) }}</span>
                </div>

                <div class="p-4 bg-white flex flex-col gap-4 overflow-y-auto">

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-green-600"><Money /></el-icon> 实收流水明细
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">现金支付</span>
                            <span class="font-bold text-gray-800">￥{{ (shiftData.cashPay || 0).toFixed(2) }}</span>
                        </div>

                        <div class="py-1 flex flex-col">
                            <div class="flex justify-between items-center w-full">
                                <span class="text-gray-600 text-sm">聚合扫码</span>
                                <span class="font-bold text-gray-800">￥{{ (shiftData.scanPay || 0).toFixed(2) }}</span>
                            </div>
                            <div v-if="shiftData.scanPayBreakdown && shiftData.scanPayBreakdown.length > 0" class="pl-2 mt-1 border-l-2 border-gray-200 flex flex-col gap-1">
                                <div v-for="(item, index) in shiftData.scanPayBreakdown" :key="index" class="flex justify-between items-center">
                                    <span class="text-gray-400 text-xs font-mono">- {{ getPayTagName(item.name) }}</span>
                                    <span class="text-gray-600 text-xs font-bold">￥{{ (item.value || 0).toFixed(2) }}</span>
                                </div>
                            </div>
                        </div>

                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">会员余额抵扣 <span class="text-xs text-gray-400">(非现支出)</span></span>
                            <span class="font-bold text-gray-800">￥{{ (shiftData.balancePay || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                    <div class="bg-green-50 p-3 rounded-lg border border-green-200 shadow-inner">
                        <div class="text-sm font-bold text-green-800 border-b border-green-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-green-600"><RefreshLeft /></el-icon> 全渠道净收核算
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-green-700 text-xs">全渠道实收总流水 <span class="text-green-600/70">(现金+扫码+余额)</span></span>
                            <span class="font-bold text-green-800 text-sm">￥{{ ((shiftData.cashPay || 0) + (shiftData.scanPay || 0) + (shiftData.balancePay || 0)).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-green-700 text-xs">减：售后退款冲回 <span class="text-green-600/70">(含各渠道)</span></span>
                            <span class="font-bold text-red-500 text-sm">- ￥{{ (shiftData.refundAmount || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center border-t border-green-200 pt-2 mt-1">
                            <span class="text-green-800 font-black text-sm">最终净收总额</span>
                            <span class="font-black text-green-600 text-xl">￥{{ (shiftData.netIncome || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                    <div>
                        <div class="text-sm font-bold text-gray-700 border-b border-gray-200 pb-1 mb-2 flex items-center gap-1">
                            <el-icon class="text-orange-500"><Ticket /></el-icon> 营销与资产核销
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">会员券真实核销</span>
                            <span class="font-bold text-orange-500">￥{{ (shiftData.memberCouponPay || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">满减券抵扣 <span class="text-xs text-gray-400">(共 {{ shiftData.voucherCount || 0 }} 张)</span></span>
                            <span class="font-bold text-red-500">￥{{ (shiftData.voucherDiscount || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">店铺免券让利 <span class="text-xs text-gray-400">(免收差价)</span></span>
                            <span class="font-bold text-blue-500">￥{{ (shiftData.waivedCouponAmount || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-1">
                            <span class="text-gray-600 text-sm">手工整单优惠 <span class="text-xs text-gray-400">(抹零)</span></span>
                            <span class="font-bold text-teal-600">￥{{ (shiftData.manualDiscount || 0).toFixed(2) }}</span>
                        </div>
                    </div>

                </div>
            </div>
        </div>

        <template #footer>
            <div class="flex justify-between items-center mt-2 px-2 border-t pt-4">
                <span class="text-xs text-gray-500 font-bold"><el-icon class="text-orange-500"><Warning /></el-icon> 请核对钱箱现金是否一致</span>
                <div class="flex gap-3">
                    <el-button @click="printShift" size="large" class="font-bold text-gray-600"><el-icon class="mr-1"><Printer /></el-icon> 打印交班单</el-button>
                    <el-button type="danger" size="large" @click="confirmShift" class="font-black px-6 tracking-widest shadow-md" :disabled="!shiftData"><el-icon class="mr-1"><SwitchButton /></el-icon> 确认交班</el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Avatar, List, Printer, SwitchButton, Warning, Money, Wallet, Ticket, DataAnalysis, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import financeApi from "@/api/finance/finance.js"
import dictApi from "@/api/system/dict.js"

const props = defineProps(['modelValue', 'cashierName'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const loading = ref(false)
const startTime = ref('')
const shiftData = ref(null)
const payTagDict = ref([])
const searchCashierName = ref('全部收银员')

const getPayTagName = (code) => {
    if (!code || code === 'UNKNOWN') return '未分类扫码'
    const match = payTagDict.value.find(t => t.value === code || t.dictValue === code)
    return match ? (match.desc || match.dictLabel) : code
}

const initShift = async () => {
    startTime.value = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss')
    try {
        const dictRes = await dictApi.loadDict(["paySubTag"])
        if (dictRes && dictRes.paySubTag) {
            payTagDict.value = dictRes.paySubTag
        }
    } catch (e) {}

    fetchData()
}

const fetchData = async () => {
    if (!startTime.value) return
    loading.value = true
    try {
        const cName = searchCashierName.value || props.cashierName || '全部收银员'
        const res = await financeApi.getShiftHandover({ startTime: startTime.value, cashierName: cName })
        shiftData.value = res.data || res
        if(!shiftData.value.cashierName) shiftData.value.cashierName = cName;
    } catch (error) {
        ElMessage.error("获取交班数据失败")
    } finally {
        loading.value = false
    }
}

// 🌟 核心回归：完全摒弃 HTML，一键调用后端底层硬件！
const printShift = async () => {
    try {
        await financeApi.printShiftHandover({
            startTime: startTime.value,
            cashierName: shiftData.value?.cashierName || '全部收银员'
        })
        ElMessage.success('底层硬件打印指令已发送！正在吐纸...')
    } catch (e) {
        ElMessage.error('发送硬件指令失败，请检查网络或打印机状态')
    }
}

const confirmShift = async () => {
    try {
        await ElMessageBox.confirm('确认后将自动静默打印【交班对账单】并清空当前收银台，是否继续？', '交接班确认', {
            confirmButtonText: '确定交班', cancelButtonText: '取消', type: 'warning'
        })

        await printShift(); // 静默触发硬件吐纸

        ElMessage.success('交班成功！辛苦了！')
        visible.value = false

        setTimeout(() => {
            window.location.reload()
        }, 1500)
    } catch (e) { }
}
</script>

<style scoped>
:deep(.el-dialog__header) {
    margin-bottom: 0;
    padding-bottom: 10px;
    border-bottom: 1px solid #f3f4f6;
}
:deep(.el-dialog__body) {
    padding-top: 15px;
}
</style>