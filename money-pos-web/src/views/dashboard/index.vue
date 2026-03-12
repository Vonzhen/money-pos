<template>
    <PageWrapper>
        <el-tabs v-model="activeName" class="demo-tabs mb-4">
            <el-tab-pane v-for="(hc, index) in homeCount" :key="index" :label="hc.label" :name="hc.name">
                <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
                    <el-card v-for="(item, idx) in hc.data" :key="idx" shadow="hover" class="border-none shadow-sm rounded-lg">
                        <template #header>
                            <span class="font-bold text-gray-600">{{ hc.label + item.title }}</span>
                        </template>
                        <div class="flex justify-between items-center mt-1">
                            <svg-icon :name="item.icon" class="w-10 h-10 text-blue-500 opacity-80" />

                            <MoneyDisplay v-if="item.isMoney" :value="item.count" size="2xl" split custom-class="text-[#1e293b]" />
                            <span v-else class="font-black text-2xl text-[#1e293b]">{{ item.count }}</span>
                        </div>
                    </el-card>

                    <el-card shadow="hover" class="border-none shadow-sm rounded-lg">
                        <template #header>
                            <span class="font-bold text-gray-600">当前库存总货值</span>
                        </template>
                        <div class="flex justify-between items-center mt-1">
                            <svg-icon name="home-inventoryValue" class="w-10 h-10 text-blue-500 opacity-80" />
                            <MoneyDisplay :value="inventoryValue" size="2xl" split custom-class="text-[#1e293b]" />
                        </div>
                    </el-card>
                </div>
            </el-tab-pane>
        </el-tabs>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4" v-loading="chartLoading">
            <el-card shadow="hover" class="lg:col-span-2 border-none shadow-sm rounded-lg">
                <template #header>
                    <div class="flex justify-between items-center">
                        <span class="font-bold text-gray-700 flex items-center gap-2">📈 近7日营业趋势</span>
                    </div>
                </template>
                <div ref="trendChartRef" class="w-full h-[300px]"></div>
            </el-card>

            <el-card shadow="hover" class="border-none shadow-sm rounded-lg">
                <template #header>
                    <div class="flex justify-between items-center">
                        <span class="font-bold text-gray-700 flex items-center gap-2">🍩 品牌矩阵营收贡献比</span>
                    </div>
                </template>
                <div ref="brandPieChartRef" class="w-full h-[300px]"></div>
            </el-card>

            <el-card shadow="hover" class="lg:col-span-3 border-none shadow-sm rounded-lg mt-2">
                <template #header>
                    <div class="flex justify-between items-center">
                        <span class="font-bold text-gray-700 flex items-center gap-2">👑 品牌高等级会员分布矩阵</span>
                    </div>
                </template>
                <div ref="memberBarChartRef" class="w-full h-[280px]"></div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { onMounted, onUnmounted, ref, nextTick } from "vue";
import homeApi from "@/api/dashboard/home.js";
import dictApi from "@/api/system/dict.js";
import * as echarts from 'echarts';

const activeName = ref('today')
const homeCount = ref([])
const inventoryValue = ref(0)
const chartLoading = ref(true)

const memberTypes = ref([])
const chartsData = ref({})

const trendChartRef = ref(null)
const brandPieChartRef = ref(null)
const memberBarChartRef = ref(null)
let charts = []

const initDashboardData = async () => {
    chartLoading.value = true;
    try {
        const dataRes = await homeApi.getHomeCount()
        const data = dataRes.data || dataRes
        inventoryValue.value = data.inventoryValue || 0

        // 🌟 增加了 isMoney 标记，让模板知道谁该用 MoneyDisplay 渲染
        const flatGet = (d) => {
            if (!d) return []
            const aov = d.orderCount > 0 ? (d.saleCount / d.orderCount) : 0;
            return [
                { title: '订单数', icon: 'home-order', count: d.orderCount || 0, isMoney: false },
                { title: '销售额', icon: 'home-sale', count: d.saleCount || 0, isMoney: true },
                { title: '净利润', icon: 'home-profit', count: d.profit || 0, isMoney: true },
                { title: '客单价', icon: 'home-sale', count: aov, isMoney: true }
            ]
        }

        homeCount.value = [
            {label: '今日', name: 'today', data: flatGet(data.today)},
            {label: '本月', name: 'month', data: flatGet(data.month)},
            {label: '本年', name: 'year', data: flatGet(data.year)},
            {label: '总计', name: 'total', data: flatGet(data.total)}
        ]

        const dictRes = await dictApi.loadDict(["memberType"])
        memberTypes.value = (dictRes.memberType || []).filter(item => item.value !== 'MEMBER')

        const chartRes = await homeApi.getChartsData()
        chartsData.value = chartRes.data || chartRes || {}

    } catch (error) {
        console.error("Dashboard 初始化异常:", error)
    } finally {
        chartLoading.value = false;
        nextTick(() => {
            drawAllCharts();
        })
    }
}

onMounted(() => {
    initDashboardData();
    window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    charts.forEach(chart => chart?.dispose())
})

const handleResize = () => { charts.forEach(chart => chart?.resize()) }

const drawAllCharts = () => {
    // 1. 走势图
    if (trendChartRef.value) {
        const trend = chartsData.value.trendData || []
        const dates = trend.map(item => item.date)
        const sales = trend.map(item => item.sales)
        const profits = trend.map(item => item.profit)

        const trendChart = echarts.init(trendChartRef.value)
        trendChart.setOption({
            tooltip: { trigger: 'axis', appendToBody: true },
            legend: { data: ['销售额', '净利润'], bottom: 0 },
            grid: { left: '3%', right: '4%', bottom: '10%', top: '5%', containLabel: true },
            xAxis: { type: 'category', boundaryGap: false, data: dates.length ? dates : ['无数据'] },
            yAxis: { type: 'value' },
            series: [
                { name: '销售额', type: 'line', smooth: true, itemStyle: { color: '#3b82f6' }, areaStyle: { color: 'rgba(59, 130, 246, 0.1)' }, data: sales },
                { name: '净利润', type: 'line', smooth: true, itemStyle: { color: '#10b981' }, data: profits }
            ]
        })
        charts.push(trendChart)
    }

    // 2. 饼图
    if (brandPieChartRef.value) {
        let pie = chartsData.value.pieData || []
        pie = pie.filter(item => item.name && item.name !== '1' && item.name !== '无品牌/未知' && !item.name.includes('套餐'))

        const pieChart = echarts.init(brandPieChartRef.value)
        pieChart.setOption({
            tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: ￥{c} ({d}%)', appendToBody: true },
            legend: { type: 'scroll', top: '5%', left: 'center' },
            series: [{
                name: '品牌营收贡献', type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: false,
                itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                label: { show: false, position: 'center' },
                emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
                labelLine: { show: false },
                data: pie.length ? pie : [{name: '暂无数据', value: 0}]
            }]
        })
        charts.push(pieChart)
    }

    // 3. 柱状图
    if (memberBarChartRef.value) {
        let barData = chartsData.value.barData || []
        barData = barData.filter(item => item.brandName && item.brandName !== '1' && item.brandName !== '无品牌/未知' && !item.brandName.includes('套餐'))

        const brandNames = [...new Set(barData.map(item => item.brandName))]
        const allLevelCodes = [...new Set(barData.map(item => item.levelCode))]

        const validLevelCodes = allLevelCodes.filter(code =>
            memberTypes.value.some(m => m.value === code)
        )

        const legendData = []
        const barSeries = validLevelCodes.map(code => {
            const dictItem = memberTypes.value.find(m => m.value === code)
            const cnName = dictItem.desc
            legendData.push(cnName)

            const counts = brandNames.map(brand => {
                const match = barData.find(d => d.brandName === brand && d.levelCode === code)
                return match ? match.count : 0
            })
            return {
                name: cnName,
                type: 'bar',
                stack: 'total',
                barWidth: '40%',
                label: {
                    show: true,
                    formatter: function(params) {
                        return params.value > 0 ? params.value : '';
                    }
                },
                data: counts
            }
        })

        const barChart = echarts.init(memberBarChartRef.value)
        barChart.setOption({
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, appendToBody: true },
            legend: { data: legendData.length ? legendData : ['无数据'], top: 0 },
            grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
            xAxis: {
                type: 'value',
                minInterval: 1,
                axisLabel: { formatter: '{value}' }
            },
            yAxis: {
                type: 'category',
                data: brandNames.length ? brandNames : ['暂无品牌'],
                axisTick: { show: false },
                axisLine: { lineStyle: { color: '#ccc' } },
                axisLabel: { color: '#666', fontWeight: 'bold' }
            },
            series: barSeries.length ? barSeries : [{ name: '无数据', type: 'bar', data: [0] }]
        })
        charts.push(barChart)
    }
}
</script>