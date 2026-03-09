<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-blue-700 flex items-center">
                        <el-icon class="mr-2"><DataAnalysis /></el-icon> 门店经营作战室 (销售大盘)
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">深度剖析门店商品流转效率、客单价走势与品牌贡献度</p>
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

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📈 期间营业额与单量双轨走势">
                    <div ref="trendChartRef" style="height: 380px; width: 100%;"></div>
                </el-card>

                <el-card shadow="hover" class="rounded-lg" header="🏅 品牌阵营销售额贡献比">
                    <div ref="brandPieChartRef" style="height: 380px; width: 100%;"></div>
                </el-card>
            </div>

            <el-card shadow="hover" class="rounded-lg" header="🔥 Top 50 门店畅销爆品榜单 (按动销件数)">
                <el-table :data="data.topGoodsRanking" height="500" stripe style="width: 100%" v-loading="loading">
                    <el-table-column type="index" label="热度" width="80" align="center">
                        <template #default="scope">
                            <span v-if="scope.$index < 3" class="text-xl text-red-500 font-black">🔥 {{ scope.$index + 1 }}</span>
                            <span v-else class="text-gray-500 font-bold">{{ scope.$index + 1 }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="goodsName" label="商品名称" min-width="200" show-overflow-tooltip>
                        <template #default="{row}"><span class="font-bold text-gray-800">{{ row.goodsName }}</span></template>
                    </el-table-column>
                    <el-table-column prop="salesQty" label="净售出数量" width="150" align="center" sortable>
                        <template #default="{row}">
                            <el-tag type="danger" effect="dark" class="font-bold">{{ row.salesQty }} 件</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="salesAmount" label="拉动营业额" width="150" align="right" sortable>
                        <template #default="{row}">
                            <span class="font-bold text-blue-600">¥ {{ formatMoney(row.salesAmount) }}</span>
                        </template>
                    </el-table-column>
                </el-table>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
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
    topGoodsRanking: [], brandDistribution: [], trendDates: [], trendSales: [], trendOrders: []
})

const trendChartRef = ref(null)
const brandPieChartRef = ref(null)
let trendChart = null
let brandPieChart = null

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        // 🌟 核心：直接请求 oms/analysis 接口
        const res = await req({ url: '/oms/analysis/dashboard', method: 'GET', params: { startDate, endDate } })
        data.value = res?.data || res || {}
        nextTick(() => {
            initTrendChart()
            initBrandPieChart()
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

const initBrandPieChart = () => {
    if (!brandPieChartRef.value) return
    if (!brandPieChart) brandPieChart = echarts.init(brandPieChartRef.value)

    const pieData = (data.value.brandDistribution || []).map(b => ({ name: b.brandName, value: b.salesAmount }))

    brandPieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: ¥ {c} ({d}%)', appendToBody: true },
        legend: { type: 'scroll', orient: 'horizontal', bottom: '0' },
        series: [{
            name: '品牌业绩贡献', type: 'pie', radius: ['35%', '65%'],
            center: ['50%', '45%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
            label: { show: false },
            data: pieData
        }]
    })
}

const handleResize = () => {
    if (trendChart) trendChart.resize()
    if (brandPieChart) brandPieChart.resize()
}

onMounted(() => {
    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (trendChart) trendChart.dispose()
    if (brandPieChart) brandPieChart.dispose()
})
</script>