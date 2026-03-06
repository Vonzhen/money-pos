<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <h2 class="text-2xl font-bold text-gray-800">📊 门店今日财务大屏</h2>
                <el-button type="primary" plain @click="fetchData">
                    <el-icon class="mr-1"><Refresh /></el-icon> 实时刷新
                </el-button>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
                <el-card shadow="hover" class="border-t-4 border-t-blue-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-medium">今日实收 (扫码/现金)</div>
                            <div class="text-3xl font-bold text-blue-600">¥ {{ formatMoney(data.todayIncome) }}</div>
                        </div>
                        <div class="p-3 bg-blue-50 rounded-full"><el-icon class="text-2xl text-blue-500"><Wallet /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400">除去会员余额抵扣的真实进账</div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-green-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-medium">今日毛利润</div>
                            <div class="text-3xl font-bold text-green-600">¥ {{ formatMoney(data.todayProfit) }}</div>
                        </div>
                        <div class="p-3 bg-green-50 rounded-full"><el-icon class="text-2xl text-green-500"><DataLine /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400">扣除所有进货成本后的净赚金额</div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-red-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-medium">今日活动让利</div>
                            <div class="text-3xl font-bold text-red-500">¥ {{ formatMoney(data.todayDiscount) }}</div>
                        </div>
                        <div class="p-3 bg-red-50 rounded-full"><el-icon class="text-2xl text-red-500"><Present /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400">会员折扣 + 满减券 + 会员券抵扣总额</div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-purple-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-medium">全店隐形总负债</div>
                            <div class="text-3xl font-bold text-purple-600">¥ {{ formatMoney(data.totalDebt) }}</div>
                        </div>
                        <div class="p-3 bg-purple-50 rounded-full"><el-icon class="text-2xl text-purple-500"><Warning /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400">全体会员卡内尚未消费的沉淀本金</div>
                </el-card>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📈 近 7 天真实进账趋势 (扫码+现金)">
                    <div ref="lineChartRef" style="height: 350px; width: 100%;"></div>
                </el-card>

                <el-card shadow="hover" class="rounded-lg" header="🧮 今日收款渠道分布">
                    <div ref="pieChartRef" style="height: 350px; width: 100%;"></div>
                    <div v-if="!data.payBreakdown || data.payBreakdown.length === 0" class="text-center text-gray-400 text-sm mt-[-180px]">
                        今日暂无除余额外的收款记录
                    </div>
                </el-card>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import financeApi from "@/api/finance/finance.js"
import { Refresh, Wallet, DataLine, Present, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts' // 引入 ECharts

const data = ref({
    todayIncome: 0, todayProfit: 0, todayDiscount: 0, totalDebt: 0,
    payBreakdown: [], trendDates: [], trendIncomes: []
})

const lineChartRef = ref(null)
const pieChartRef = ref(null)
let lineChart = null
let pieChart = null

const fetchData = async () => {
    try {
        const res = await financeApi.getDashboardData()
        data.value = res.data || {}

        // 数据回来后，立即渲染图表
        nextTick(() => {
            initLineChart()
            initPieChart()
        })
    } catch (error) {
        ElMessage.error("获取财务数据失败，请检查网络")
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

// 绘制折线图
const initLineChart = () => {
    if (!lineChartRef.value) return
    if (!lineChart) lineChart = echarts.init(lineChartRef.value)

    lineChart.setOption({
        tooltip: { trigger: 'axis', formatter: '{b} <br/> 进账: ¥ {c}' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: data.value.trendDates || [] },
        yAxis: { type: 'value', name: '金额 (元)' },
        series: [{
            name: '营业额', type: 'line', smooth: true,
            areaStyle: { color: 'rgba(64, 158, 255, 0.2)' },
            itemStyle: { color: '#409EFF' },
            data: data.value.trendIncomes || []
        }]
    })
}

// 绘制渠道饼图
const initPieChart = () => {
    if (!pieChartRef.value) return
    if (!pieChart) pieChart = echarts.init(pieChartRef.value)

    pieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: ¥ {c} ({d}%)' },
        legend: { orient: 'horizontal', bottom: 'bottom' },
        series: [{
            name: '收款渠道', type: 'pie', radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
            label: { show: false, position: 'center' },
            emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold' } },
            labelLine: { show: false },
            data: data.value.payBreakdown || []
        }]
    })
}

// 监听窗口大小变化，让图表自适应缩放
window.addEventListener('resize', () => {
    if (lineChart) lineChart.resize()
    if (pieChart) pieChart.resize()
})

onMounted(() => {
    fetchData()
})
</script>