<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800">🧮 营业额成分“挤水分”分析</h2>
                    <p class="text-sm text-gray-500 mt-1">剖析每一笔交易中，真金白银、预收款、营销让利的真实构成比例</p>
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
                        <el-icon class="mr-1"><Refresh /></el-icon> 穿透分析
                    </el-button>
                </div>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📊 营业额成分堆叠趋势 (真金与让利一目了然)">
                    <div ref="stackChartRef" style="height: 500px; width: 100%;"></div>
                </el-card>

                <el-card shadow="hover" class="rounded-lg" header="🍩 期间总体成分切片">
                    <div ref="pieChartRef" style="height: 500px; width: 100%;"></div>
                    <div v-if="!data.pieData || data.pieData.length === 0" class="text-center text-gray-400 mt-[-250px]">
                        该时间段内无数据
                    </div>
                </el-card>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)

const shortcuts = [
    { text: '最近7天', value: () => [dayjs().subtract(6, 'day').toDate(), dayjs().toDate()] },
    { text: '最近30天', value: () => [dayjs().subtract(29, 'day').toDate(), dayjs().toDate()] },
    { text: '本月', value: () => [dayjs().startOf('month').toDate(), dayjs().endOf('month').toDate()] }
]

const data = ref({
    trendDates: [], scanList: [], cashList: [], balanceList: [], couponList: [], voucherList: [], pieData: []
})

const stackChartRef = ref(null)
const pieChartRef = ref(null)
let stackChart = null
let pieChart = null

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        const res = await req({ url: '/finance/channel-mix', method: 'GET', params: { startDate, endDate } })
        data.value = res?.data || res || {}
        nextTick(() => {
            initStackChart()
            initPieChart()
        })
    } catch (error) {
        ElMessage.error("分析数据拉取失败")
    } finally {
        loading.value = false
    }
}

const colorPalette = {
    'scan': '#409EFF',    // 蓝
    'cash': '#9C27B0',    // 紫
    'balance': '#909399', // 灰
    'coupon': '#F56C6C',  // 红
    'voucher': '#E6A23C'  // 橙
}

const initStackChart = () => {
    if (!stackChartRef.value) return
    if (!stackChart) stackChart = echarts.init(stackChartRef.value)

    stackChart.setOption({
        // 🌟 核心修复：appendToBody 防止外框截断
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, appendToBody: true },
        legend: { data: ['聚合扫码(真金)', '现金收银(真金)', '余额消耗(预收)', '单品会员券(让利)', '整单满减券(让利)'], bottom: '0' },
        grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
        xAxis: { type: 'category', data: data.value.trendDates || [] },
        yAxis: { type: 'value', name: '金额成分' },
        series: [
            { name: '聚合扫码(真金)', type: 'bar', stack: 'total', itemStyle: { color: colorPalette.scan }, data: data.value.scanList || [] },
            { name: '现金收银(真金)', type: 'bar', stack: 'total', itemStyle: { color: colorPalette.cash }, data: data.value.cashList || [] },
            { name: '余额消耗(预收)', type: 'bar', stack: 'total', itemStyle: { color: colorPalette.balance }, data: data.value.balanceList || [] },
            { name: '单品会员券(让利)', type: 'bar', stack: 'total', itemStyle: { color: colorPalette.coupon }, data: data.value.couponList || [] },
            { name: '整单满减券(让利)', type: 'bar', stack: 'total', itemStyle: { color: colorPalette.voucher, borderRadius: [4, 4, 0, 0] }, data: data.value.voucherList || [] }
        ]
    })
}

const initPieChart = () => {
    if (!pieChartRef.value) return
    if (!pieChart) pieChart = echarts.init(pieChartRef.value)

    pieChart.setOption({
        // 🌟 核心修复：appendToBody 防止外框截断
        tooltip: { trigger: 'item', formatter: '{b}: ¥ {c} ({d}%)', appendToBody: true },
        legend: { orient: 'horizontal', bottom: 'bottom' },
        color: [colorPalette.scan, colorPalette.cash, colorPalette.balance, colorPalette.coupon, colorPalette.voucher],
        series: [{
            name: '营业成分切片', type: 'pie', radius: ['35%', '65%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, formatter: '{b}\n{d}%' },
            data: data.value.pieData || []
        }]
    })
}

const handleResize = () => {
    if (stackChart) stackChart.resize()
    if (pieChart) pieChart.resize()
}

onMounted(() => {
    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (stackChart) stackChart.dispose()
    if (pieChart) pieChart.dispose()
})
</script>