<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-blue-700 flex items-center">
                        <el-icon class="mr-2"><DataAnalysis /></el-icon> 门店经营销售大盘
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">深度剖析门店商品流转效率与大盘营业走势</p>
                </div>
                <div class="flex gap-4">
                    <el-date-picker
                        v-model="dateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="开始日期"
                        end-placeholder="结束日期"
                        value-format="YYYY-MM-DD"
                        :shortcuts="shortcuts"
                        @change="fetchData"
                        class="!w-[300px]"
                    />
                    <el-button type="primary" @click="fetchData" :loading="loading">
                        <el-icon class="mr-1"><Aim /></el-icon> 更新大盘
                    </el-button>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-6">
                <el-card shadow="hover" class="border-l-4 border-l-blue-500 bg-white">
                    <div class="text-sm text-gray-500 font-bold mb-1">期间总销售额 (实收)</div>
                    <div class="text-3xl font-mono font-black text-blue-600">¥ {{ formatMoney(data.totalSalesAmount) }}</div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-orange-500 bg-white">
                    <div class="text-sm text-gray-500 font-bold mb-1">接单笔数 (客流量)</div>
                    <div class="text-3xl font-mono font-black text-orange-600">{{ data.totalOrderCount }} <span class="text-sm text-gray-400">单</span></div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-green-500 bg-white">
                    <div class="text-sm text-gray-500 font-bold mb-1">平均客单价 (ASP)</div>
                    <div class="text-3xl font-mono font-black text-green-600">¥ {{ formatMoney(data.avgOrderValue) }}</div>
                </el-card>

                <el-card shadow="hover" class="border-l-4 border-l-purple-500 bg-white">
                    <div class="text-sm text-gray-500 font-bold mb-1">出库商品总件数</div>
                    <div class="text-3xl font-mono font-black text-purple-600">{{ data.totalGoodsCount }} <span class="text-sm text-gray-400">件</span></div>
                </el-card>
            </div>

            <el-card shadow="hover" class="rounded-lg mb-6" header="📈 期间营业额与单量双轨走势">
                <div ref="trendChartRef" style="height: 450px; width: 100%;"></div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue' // 必须是 onBeforeUnmount
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { DataAnalysis, Aim } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([
    dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)

const shortcuts = [
    { text: '最近7天', value: () => [dayjs().subtract(6, 'day').toDate(), dayjs().toDate()] },
    { text: '最近30天', value: () => [dayjs().subtract(29, 'day').toDate(), dayjs().toDate()] },
    { text: '本月', value: () => [dayjs().startOf('month').toDate(), dayjs().endOf('month').toDate()] }
]

const data = ref({
    totalSalesAmount: 0, totalOrderCount: 0, totalGoodsCount: 0, avgOrderValue: 0,
    trendDates: [], trendSales: [], trendOrders: []
})

const trendChartRef = ref(null)
let trendChart = null // 缓存实例

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        const res = await req({ url: '/oms/analysis/dashboard', method: 'GET', params: { startDate, endDate } })
        data.value = res?.data || res || {}
        nextTick(() => {
            initTrendChart()
        })
    } catch (error) {
        ElMessage.error("获取销售大盘数据失败")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const initTrendChart = () => {
    if (!trendChartRef.value) return
    if (!trendChart) trendChart = echarts.init(trendChartRef.value)

    trendChart.setOption({
        tooltip: { trigger: 'axis', appendToBody: true },
        legend: { data: ['营业总额 (元)', '接单笔数 (单)'], bottom: '0' },
        grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: data.value.trendDates || [] },
        yAxis: [
            { type: 'value', name: '金额 (元)', position: 'left' },
            { type: 'value', name: '单量 (单)', position: 'right', splitLine: { show: false } }
        ],
        series: [
            {
                name: '营业总额 (元)', type: 'line', smooth: true, yAxisIndex: 0,
                areaStyle: { color: 'rgba(64, 158, 255, 0.2)' },
                itemStyle: { color: '#409EFF' },
                data: data.value.trendSales || []
            },
            {
                name: '接单笔数 (单)', type: 'bar', barWidth: '30%', yAxisIndex: 1,
                itemStyle: { color: '#E6A23C', borderRadius: [2, 2, 0, 0] },
                data: data.value.trendOrders || []
            }
        ]
    })
}

const handleResize = () => {
    if (trendChart) trendChart.resize()
}

onMounted(() => {
    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (trendChart) {
        trendChart.dispose()
        trendChart = null
    }
})
</script>