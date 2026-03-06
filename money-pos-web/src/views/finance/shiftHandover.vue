<template>
    <PageWrapper>
        <div class="p-6 bg-gray-100 min-h-full flex flex-col items-center">

            <el-card shadow="hover" class="w-full max-w-4xl mb-6 rounded-lg border-t-4 border-t-indigo-500">
                <div class="flex flex-col md:flex-row items-center justify-between gap-4">
                    <div class="flex items-center gap-3">
                        <el-icon class="text-3xl text-indigo-500"><Clock /></el-icon>
                        <div>
                            <h2 class="text-xl font-bold text-gray-800">收银交接班核对</h2>
                            <p class="text-sm text-gray-500">选择本班次的开始时间，系统将自动核算至今的所有实收账款</p>
                        </div>
                    </div>

                    <div class="flex items-center gap-2">
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
                        <el-button type="primary" @click="fetchData" :loading="loading">
                            核算账单
                        </el-button>
                    </div>
                </div>
            </el-card>

            <div class="w-full max-w-2xl bg-white shadow-xl rounded-sm p-8 border border-gray-200 relative overflow-hidden print-area" id="print-section">
                <div class="absolute top-0 left-0 w-full h-2 bg-repeat-x" style="background-image: radial-gradient(circle, #f3f4f6 4px, transparent 5px); background-size: 12px 10px;"></div>

                <div class="text-center mb-8 mt-4">
                    <h1 class="text-3xl font-black text-gray-800 tracking-wider">门店交接班对账单</h1>
                    <p class="text-gray-500 mt-2 text-sm border-b border-dashed border-gray-300 pb-4">
                        打印时间: {{ currentTime }}
                    </p>
                </div>

                <div class="space-y-6" v-loading="loading">
                    <div class="bg-gray-50 p-4 rounded text-sm text-gray-700">
                        <div class="flex justify-between mb-2">
                            <span>接班时间 (起):</span>
                            <span class="font-bold">{{ data.shiftStartTime || '--' }}</span>
                        </div>
                        <div class="flex justify-between">
                            <span>交班时间 (止):</span>
                            <span class="font-bold">{{ data.shiftEndTime || '--' }}</span>
                        </div>
                    </div>

                    <div class="text-center py-6 bg-indigo-50 rounded-lg border border-indigo-100">
                        <div class="text-sm text-indigo-600 font-bold mb-1">本班次实收总额 (需核对)</div>
                        <div class="text-5xl font-black text-indigo-600">
                            ¥ {{ formatMoney(data.expectedTotalIncome) }}
                        </div>
                        <div class="text-xs text-indigo-400 mt-2">※ 已自动剔除会员本金扣除部分，此为真实进账款</div>
                    </div>

                    <div class="mt-6">
                        <h3 class="text-lg font-bold text-gray-800 mb-3 flex items-center border-b border-gray-200 pb-2">
                            <el-icon class="mr-2"><List /></el-icon> 资金来源明细 (抽屉/手机核对)
                        </h3>

                        <div v-if="data.payBreakdown && data.payBreakdown.length > 0">
                            <div v-for="(item, index) in data.payBreakdown" :key="index"
                                class="flex justify-between items-center py-3 border-b border-dashed border-gray-200 last:border-0">
                                <span class="text-gray-600 font-medium text-base">{{ item.name }}</span>
                                <span class="text-gray-900 font-bold text-lg">¥ {{ formatMoney(item.value) }}</span>
                            </div>
                        </div>
                        <el-empty v-else description="本班次暂无收款流水" :image-size="60" />
                    </div>

                    <div class="mt-12 pt-8 border-t border-gray-800 flex justify-between px-4">
                        <div class="text-gray-600">交班人签字：<span class="inline-block w-24 border-b border-gray-400"></span></div>
                        <div class="text-gray-600">接班人签字：<span class="inline-block w-24 border-b border-gray-400"></span></div>
                    </div>
                </div>

                <div class="absolute bottom-0 left-0 w-full h-2 bg-repeat-x" style="background-image: radial-gradient(circle, #f3f4f6 4px, transparent 5px); background-size: 12px 10px; transform: rotate(180deg);"></div>
            </div>

            <div class="mt-8">
                <el-button type="primary" size="large" class="w-48 !text-lg" @click="handlePrint">
                    <el-icon class="mr-2"><Printer /></el-icon> 打印交班条
                </el-button>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import financeApi from "@/api/finance/finance.js"
import { Clock, List, Printer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// 默认接班时间设为今天早上的 08:00:00
const getTodayMorning = () => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} 08:00:00`
}

const shiftStartTime = ref(getTodayMorning())
const loading = ref(false)
const data = ref({
    shiftStartTime: '',
    shiftEndTime: '',
    expectedTotalIncome: 0,
    payBreakdown: []
})

const currentTime = computed(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`
})

const fetchData = async () => {
    if (!shiftStartTime.value) return
    loading.value = true
    try {
        const res = await financeApi.getShiftHandover(shiftStartTime.value)
        data.value = res.data || {}
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

// 调用浏览器原生打印，只打印中间的小票区域
const handlePrint = () => {
    const printContent = document.getElementById('print-section').innerHTML
    const originalContent = document.body.innerHTML

    document.body.innerHTML = `
        <div style="padding: 20px; max-width: 400px; margin: 0 auto; font-family: sans-serif;">
            ${printContent}
        </div>
    `
    window.print()
    document.body.innerHTML = originalContent
    window.location.reload() // 打印完恢复原状
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
/* 隐藏网页打印时自带的页眉页脚 */
@media print {
    @page { margin: 0; }
    body { margin: 1.6cm; }
}
</style>