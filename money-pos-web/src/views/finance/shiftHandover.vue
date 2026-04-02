<template>
    <PageWrapper>
        <div class="p-6 bg-gray-100 min-h-full flex flex-col items-center font-sans">

            <el-card shadow="hover" class="w-full max-w-4xl mb-6 rounded-lg border-t-4 border-t-indigo-500">
                <div class="flex flex-col md:flex-row items-center justify-between gap-4">
                    <div class="flex items-center gap-3">
                        <el-icon class="text-3xl text-indigo-500"><Clock /></el-icon>
                        <div>
                            <h2 class="text-xl font-bold text-gray-800">门店交接班核算</h2>
                        </div>
                    </div>

                    <div class="flex items-center gap-3">
                        <span class="text-sm font-medium text-gray-600">收银员:</span>
                        <el-input
                            v-model="searchCashierName"
                            placeholder="留空查全部"
                            clearable
                            class="!w-28"
                            @change="fetchData"
                        />

                        <span class="text-sm font-medium text-gray-600">接班时间:</span>
                        <el-date-picker
                            v-model="shiftStartTime"
                            type="datetime"
                            placeholder="选择接班时间"
                            format="YYYY-MM-DD HH:mm:ss"
                            value-format="YYYY-MM-DD HH:mm:ss"
                            :clearable="false"
                            class="!w-56"
                            @change="fetchData"
                        />
                        <el-button type="primary" @click="fetchData" :loading="loading">核算账单</el-button>
                    </div>
                </div>
            </el-card>

            <div class="w-full max-w-2xl bg-white shadow-2xl rounded-sm p-8 border border-gray-200 relative overflow-hidden">
                <div class="absolute top-0 left-0 w-full h-2 bg-repeat-x" style="background-image: radial-gradient(circle, #f3f4f6 4px, transparent 5px); background-size: 12px 10px;"></div>

                <div class="text-center mb-6 mt-4">
                    <h1 class="text-3xl font-black text-gray-800 tracking-wider">门店交接班对账单</h1>
                    <p class="text-gray-500 mt-2 text-sm border-b border-dashed border-gray-300 pb-4">打印时间: {{ currentTime }}</p>
                </div>

                <div class="space-y-6" v-loading="loading">
                    <div class="bg-gray-50 p-4 rounded text-sm text-gray-700 border border-gray-100">
                        <div class="flex justify-between mb-2"><span>收银员:</span><span class="font-bold">{{ data.cashierName || '当前当班收银员' }}</span></div>
                        <div class="flex justify-between mb-2"><span>接班时间 (起):</span><span class="font-bold font-mono">{{ data.shiftStartTime || '--' }}</span></div>
                        <div class="flex justify-between"><span>交班时间 (止):</span><span class="font-bold font-mono">{{ data.shiftEndTime || '--' }}</span></div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-green-600"><Money /></el-icon> 实收流水 (全额入账)
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">现金支付</span>
                            <span class="text-gray-800 font-bold text-xl">¥ {{ formatMoney(data.cashPay) }}</span>
                        </div>

                        <div class="py-2 border-b border-dashed border-gray-200 flex flex-col">
                            <div class="flex justify-between items-center w-full">
                                <span class="text-gray-600 font-medium">聚合扫码</span>
                                <span class="text-gray-800 font-bold text-xl">¥ {{ formatMoney(data.scanPay) }}</span>
                            </div>
                            <div v-if="data.scanPayBreakdown && data.scanPayBreakdown.length > 0" class="pl-3 mt-1.5 border-l-2 border-gray-200 space-y-1">
                                <div v-for="(item, idx) in data.scanPayBreakdown" :key="idx" class="flex justify-between items-center">
                                    <span class="text-gray-500 text-xs font-mono">- {{ getPayTagName(item.name) }}</span>
                                    <span class="text-gray-600 text-sm font-bold">¥ {{ formatMoney(item.value) }}</span>
                                </div>
                            </div>
                        </div>

                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">会员余额 <span class="text-xs text-gray-400 font-normal ml-1">(不入抽屉，扣除账户)</span></span>
                            <span class="text-gray-800 font-bold text-xl">¥ {{ formatMoney(data.balancePay) }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-red-600"><RefreshLeft /></el-icon> 售后退款 (资金流出)
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200 bg-red-50 px-2 rounded">
                            <span class="text-red-700 font-bold">退款冲回金额 <span class="text-xs text-red-500 font-normal ml-1">(含各渠道)</span></span>
                            <span class="text-red-600 font-bold text-xl">- ¥ {{ formatMoney(data.refundAmount) }}</span>
                        </div>
                    </div>

                    <div class="mt-6 bg-green-50 p-4 rounded-lg border border-green-200 shadow-inner">
                        <div class="flex justify-between items-center mb-2">
                            <span class="text-gray-600 font-bold text-sm">全渠道实收总流水</span>
                            <span class="text-gray-600 font-bold text-lg">¥ {{ formatMoney(NP.plus(NP.plus(data.cashPay, data.scanPay), data.balancePay)) }}</span>
                        </div>
                        <div class="flex justify-between items-center mb-2">
                            <span class="text-gray-600 font-bold text-sm">减：退款冲回总额</span>
                            <span class="text-red-500 font-bold text-lg">- ¥ {{ formatMoney(data.refundAmount) }}</span>
                        </div>
                        <div class="flex justify-between items-center border-t border-green-200 pt-2 mt-2">
                            <span class="text-green-700 font-black text-lg">全渠道净收总额 <span class="text-xs text-green-600 font-normal ml-1">(完美对齐大屏)</span></span>
                            <span class="text-green-600 font-black text-2xl">¥ {{ formatMoney(data.netIncome) }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-orange-500"><Ticket /></el-icon> 营销与资产核销
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">会员券真实核销</span>
                            <span class="text-orange-500 font-bold text-lg">¥ {{ formatMoney(data.memberCouponPay) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">满减券抵扣 <span class="text-xs text-red-500 font-bold ml-1">(共使用 {{ data.voucherCount || 0 }} 张)</span></span>
                            <span class="text-red-500 font-bold text-lg">¥ {{ formatMoney(data.voucherDiscount) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">店铺免券让利 <span class="text-xs text-gray-400 font-normal ml-1">(免收产生的差价)</span></span>
                            <span class="text-blue-500 font-bold text-lg">¥ {{ formatMoney(data.waivedCouponAmount) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">手工整单优惠 <span class="text-xs text-gray-400 font-normal ml-1">(收银抹零)</span></span>
                            <span class="text-red-500 font-bold text-lg">¥ {{ formatMoney(data.manualDiscount) }}</span>
                        </div>
                    </div>
                </div>
                <div class="absolute bottom-0 left-0 w-full h-2 bg-repeat-x" style="background-image: radial-gradient(circle, #f3f4f6 4px, transparent 5px); background-size: 12px 10px; transform: rotate(180deg);"></div>
            </div>

            <div class="mt-8 mb-10">
                <el-button type="primary" size="large" class="w-56 !text-lg shadow-lg tracking-widest font-bold" @click="handlePrint">
                    <el-icon class="mr-2"><Printer /></el-icon> 打印交班单
                </el-button>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import financeApi from "@/api/finance/finance.js"
import dictApi from "@/api/system/dict.js"
import { Clock, Printer, Money, Wallet, Ticket, DataAnalysis, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import NP from "number-precision"

const getTodayMorning = () => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} 08:00:00`
}

const shiftStartTime = ref(getTodayMorning())
const loading = ref(false)
const payTagDict = ref([])
const searchCashierName = ref('全部收银员')

const data = ref({
    shiftStartTime: '', shiftEndTime: '', cashierName: '',
    cashPay: 0, scanPay: 0, scanPayBreakdown: [],
    balancePay: 0, memberCouponPay: 0,
    voucherDiscount: 0, voucherCount: 0, manualDiscount: 0, waivedCouponAmount: 0,
    refundAmount: 0, netIncome: 0, expectedTotalIncome: 0
})

const currentTime = computed(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
})

const getPayTagName = (code) => {
    if (!code || code === 'UNKNOWN') return '未分类扫码'
    const match = payTagDict.value.find(t => t.value === code || t.dictValue === code)
    return match ? (match.desc || match.dictLabel) : code
}

const fetchData = async () => {
    if (!shiftStartTime.value) return
    loading.value = true
    try {
        const cName = searchCashierName.value || '全部收银员'
        const res = await financeApi.getShiftHandover({ startTime: shiftStartTime.value, cashierName: cName })
        data.value = res.data || res || {}
        if(!data.value.cashierName) data.value.cashierName = cName;
    } catch (error) {
        ElMessage.error("获取交班数据失败")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

// ==========================================
// 🌟 核心替换点：彻底删除了原来那一大坨拼凑 HTML 的 window.print() 代码！
// 直接调用后端静默打印 API，实现前后端统一的硬件级驱动！
// ==========================================
const handlePrint = async () => {
    try {
        await financeApi.printShiftHandover({
            startTime: shiftStartTime.value,
            cashierName: data.value?.cashierName || '全部收银员'
        })
        ElMessage.success('底层硬件打印指令已发送！打印机正在吐纸...')
    } catch (e) {
        ElMessage.error('发送硬件指令失败，请检查网络或打印机状态')
    }
}

onMounted(async () => {
    try {
        const dictRes = await dictApi.loadDict(["paySubTag"])
        if (dictRes && dictRes.paySubTag) {
            payTagDict.value = dictRes.paySubTag
        }
    } catch (e) {}

    searchCashierName.value = '全部收银员'
    fetchData()
})
</script>