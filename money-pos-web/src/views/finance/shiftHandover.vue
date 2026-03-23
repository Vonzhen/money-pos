<template>
    <PageWrapper>
        <div class="p-6 bg-gray-100 min-h-full flex flex-col items-center font-sans">

            <el-card shadow="hover" class="w-full max-w-4xl mb-6 rounded-lg border-t-4 border-t-indigo-500">
                <div class="flex flex-col md:flex-row items-center justify-between gap-4">
                    <div class="flex items-center gap-3">
                        <el-icon class="text-3xl text-indigo-500"><Clock /></el-icon>
                        <div>
                            <h2 class="text-xl font-bold text-gray-800">门店交接班核算</h2>
                            <p class="text-sm text-gray-500">基于后端结算中枢，严格分离现金、资产与营销流水</p>
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

            <div class="w-full max-w-2xl bg-white shadow-2xl rounded-sm p-8 border border-gray-200 relative overflow-hidden print-area" id="print-section">
                <div class="absolute top-0 left-0 w-full h-2 bg-repeat-x" style="background-image: radial-gradient(circle, #f3f4f6 4px, transparent 5px); background-size: 12px 10px;"></div>

                <div class="text-center mb-6 mt-4">
                    <h1 class="text-3xl font-black text-gray-800 tracking-wider">门店交接班对账单</h1>
                    <p class="text-gray-500 mt-2 text-sm border-b border-dashed border-gray-300 pb-4">打印时间: {{ currentTime }}</p>
                </div>

                <div class="space-y-6" v-loading="loading">

                    <div class="bg-gray-50 p-4 rounded text-sm text-gray-700 border border-gray-100">
                        <div class="flex justify-between mb-2">
                            <span>收银员:</span><span class="font-bold">{{ data.cashierName || '当前当班收银员' }}</span>
                        </div>
                        <div class="flex justify-between mb-2">
                            <span>接班时间 (起):</span><span class="font-bold font-mono">{{ data.shiftStartTime || '--' }}</span>
                        </div>
                        <div class="flex justify-between">
                            <span>交班时间 (止):</span><span class="font-bold font-mono">{{ data.shiftEndTime || '--' }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-green-600"><Money /></el-icon> 实收对账 (应存现额)
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">现金支付</span>
                            <span class="text-green-600 font-bold text-xl">¥ {{ formatMoney(data.cashPay) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">扫码支付</span>
                            <span class="text-green-600 font-bold text-xl">¥ {{ formatMoney(data.scanPay) }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-blue-500"><Wallet /></el-icon> 资产核销 (非现支出)
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">会员余额 <span class="text-xs text-gray-400 font-normal ml-1">(核对会员账户变动)</span></span>
                            <span class="text-gray-900 font-bold text-lg">¥ {{ formatMoney(data.balancePay) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">会员券扣减 <span class="text-xs text-gray-400 font-normal ml-1">(核对品牌资产消耗)</span></span>
                            <span class="text-gray-900 font-bold text-lg">¥ {{ formatMoney(data.memberCouponPay) }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-orange-500"><Ticket /></el-icon> 营销成本 (放血记录)
                        </h3>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">满减券 <span class="text-xs text-gray-400 font-normal ml-1">(共 {{ data.voucherCount || 0 }} 张)</span></span>
                            <span class="text-orange-500 font-bold text-lg">¥ {{ formatMoney(data.voucherDiscount) }}</span>
                        </div>
                        <div class="flex justify-between items-center py-2 border-b border-dashed border-gray-200">
                            <span class="text-gray-600 font-medium">整单优惠 <span class="text-xs text-gray-400 font-normal ml-1">(手动抹零)</span></span>
                            <span class="text-teal-600 font-bold text-lg">¥ {{ formatMoney(data.manualDiscount) }}</span>
                        </div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-2 flex items-center border-b border-gray-300 pb-2">
                            <el-icon class="mr-2 text-purple-500"><DataAnalysis /></el-icon> 品牌贡献矩阵
                        </h3>
                        <div v-if="data.brandMatrix && data.brandMatrix.length > 0" class="bg-gray-50 rounded border border-gray-200 p-2">
                            <div v-for="(brand, idx) in data.brandMatrix" :key="idx" class="flex justify-between items-center py-2 px-2 border-b border-dashed border-gray-200 last:border-0">
                                <span class="text-gray-800 font-bold">{{ brand.brandName }}</span>
                                <span class="text-sm text-gray-600">
                                    营业额 <b class="text-gray-900">¥{{ formatMoney(brand.revenue) }}</b> /
                                    券耗 <b class="text-red-500">¥{{ formatMoney(brand.couponConsumption) }}</b>
                                </span>
                            </div>
                        </div>
                        <div v-else class="text-center text-gray-400 py-4 text-sm">本班次暂无品牌销售数据</div>
                    </div>

                    <div class="mt-10 pt-6 border-t border-gray-800 flex justify-between px-6">
                        <div class="text-gray-600 font-bold tracking-widest">交班人：<span class="inline-block w-28 border-b border-gray-400"></span></div>
                        <div class="text-gray-600 font-bold tracking-widest">接班人：<span class="inline-block w-28 border-b border-gray-400"></span></div>
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
import { Clock, Printer, Money, Wallet, Ticket, DataAnalysis } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from "@/store/index.js"

const userStore = useUserStore()

const getTodayMorning = () => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} 08:00:00`
}

const shiftStartTime = ref(getTodayMorning())
const loading = ref(false)

// 🌟 核心修复 2：定义搜索变量，默认查全店大盘数据
const searchCashierName = ref('全部收银员')

const data = ref({
    shiftStartTime: '', shiftEndTime: '', cashierName: '',
    cashPay: 0, scanPay: 0,
    balancePay: 0, memberCouponPay: 0,
    voucherDiscount: 0, voucherCount: 0, manualDiscount: 0,
    brandMatrix: []
})

const currentTime = computed(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
})

const fetchData = async () => {
    if (!shiftStartTime.value) return
    loading.value = true
    try {
        // 🌟 核心修复 3：不再强制使用当前登录人，而是使用搜索框的值，为空则自动设为"全部收银员"
        const cName = searchCashierName.value || '全部收银员'

        const res = await financeApi.getShiftHandover({ startTime: shiftStartTime.value, cashierName: cName })
        data.value = res.data || res || {}

        // 强制回显到界面，避免后端返回为空时前端显示默认的'当前当班收银员'
        if(!data.value.cashierName) {
            data.value.cashierName = cName;
        }
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

const handlePrint = () => {
    const printContent = document.getElementById('print-section').innerHTML
    const originalContent = document.body.innerHTML

    document.body.innerHTML = `
        <div style="padding: 20px; max-width: 420px; margin: 0 auto; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;">
            ${printContent}
        </div>
    `
    window.print()
    document.body.innerHTML = originalContent
    window.location.reload()
}

onMounted(() => {
    // 🌟 如果需要的话，可以根据角色判断：如果是老板，默认查“全部收银员”；如果是店员，默认查自己
    // 这里为了兼顾后台核算，默认设为 "全部收银员"
    searchCashierName.value = '全部收银员'
    fetchData()
})
</script>

<style scoped>
@media print {
    @page { margin: 0; }
    body { margin: 1.5cm; }
}
</style>