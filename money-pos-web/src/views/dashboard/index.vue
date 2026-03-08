<template>
    <PageWrapper>
        <el-tabs v-model="activeName" class="demo-tabs mb-4">
            <el-tab-pane v-for="(hc, index) in homeCount" :key="index" :label="hc.label" :name="hc.name">
                <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
                    <el-card v-for="(item, idx) in hc.data" :key="idx" shadow="hover" class="border-none shadow-sm rounded-lg">
                        <template #header>
                            <span class="font-bold text-gray-600">{{ hc.label + item.title }}</span>
                        </template>
                        <div class="flex justify-between items-center">
                            <svg-icon :name="item.icon" class="w-10 h-10 text-blue-500 opacity-80" />
                            <el-statistic :precision="item.precision" :value="item.count" value-style="font-weight: 900; font-size: 24px; color: #1e293b;" />
                        </div>
                    </el-card>

                    <el-card shadow="hover" class="border-none shadow-sm rounded-lg">
                        <template #header>
                            <span class="font-bold text-gray-600">当前库存总货值</span>
                        </template>
                        <div class="flex justify-between items-center">
                            <svg-icon name="home-inventoryValue" class="w-10 h-10 text-blue-500 opacity-80" />
                            <el-statistic :precision="2" :value="inventoryValue" value-style="font-weight: 900; font-size: 24px; color: #1e293b;" />
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

// 🌟 重构：将数据获取与图表渲染严格按先后顺序执行
const initDashboardData = async () => {
    chartLoading.value = true;
    try {
        // 1. 获取顶部数字看板
        const dataRes = await homeApi.getHomeCount()
        const data = dataRes.data || dataRes
        inventoryValue.value = data.inventoryValue || 0

        const flatGet = (d) => {
            if (!d) return []
            const aov = d.orderCount > 0 ? (d.saleCount / d.orderCount) : 0;
            return [
                { title: '订单数', icon: 'home-order', count: d.orderCount || 0, precision: 0 },
                { title: '销售额', icon: 'home-sale', count: d.saleCount || 0, precision: 2 },
                { title: '净利润', icon: 'home-profit', count: d.profit || 0, precision: 2 },
                { title: '客单价', icon: 'home-sale', count: aov, precision: 2 }
            ]
        }
        homeCount.value = [
            {label: '今日', name: 'today', data: flatGet(data.today)},
            {label: '本月', name: 'month', data: flatGet(data.month)},
            {label: '本年', name: 'year', data: flatGet(data.year)},
            {label: '总计', name: 'total', data: flatGet(data.total)}
        ]

        // 2. 获取图表与字典数据
        const dictRes = await dictApi.loadDict(["memberType"])
        memberTypes.value = (dictRes.memberType || []).filter(item => item.value !== 'MEMBER')

        const chartRes = await homeApi.getChartsData()
        chartsData.value = chartRes.data || chartRes || {}

    } catch (error) {
        console.error("Dashboard 初始化异常:", error)
    } finally {
        chartLoading.value = false;
        // 确保 DOM 遮罩层消失后再画图，防止尺寸计算为 0
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
            tooltip: { trigger: 'axis' },
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
        const pie = chartsData.value.pieData || []
        const pieChart = echarts.init(brandPieChartRef.value)
        pieChart.setOption({
            tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: ￥{c} ({d}%)' },
            legend: { top: '5%', left: 'center' },
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

    // 3. 柱状图 (🌟 已加入历史脏数据过滤机制)
        if (memberBarChartRef.value) {
            const barData = chartsData.value.barData || []
            const brandNames = [...new Set(barData.map(item => item.brandName))]
            const allLevelCodes = [...new Set(barData.map(item => item.levelCode))]

            // 🌟 核心拦截：只保留当前字典中真实存在的等级，过滤掉 HJ_VIP 等历史脏数据
            const validLevelCodes = allLevelCodes.filter(code =>
                memberTypes.value.some(m => m.value === code)
            )

            const legendData = []
            const barSeries = validLevelCodes.map(code => {
                // 这里绝对能找到，因为上面已经过滤过了
                const dictItem = memberTypes.value.find(m => m.value === code)
                const cnName = dictItem.desc
                legendData.push(cnName)

                const counts = brandNames.map(brand => {
                    const match = barData.find(d => d.brandName === brand && d.levelCode === code)
                    return match ? match.count : 0
                })
                return { name: cnName, type: 'bar', stack: 'total', label: { show: true }, data: counts }
            })

            const barChart = echarts.init(memberBarChartRef.value)
            barChart.setOption({
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, appendToBody: true },
                legend: { data: legendData.length ? legendData : ['无数据'], top: 0 },
                grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
                xAxis: { type: 'value' },
                yAxis: { type: 'category', data: brandNames.length ? brandNames : ['暂无品牌'] },
                series: barSeries.length ? barSeries : [{ name: '无数据', type: 'bar', data: [0] }]
            })
            charts.push(barChart)
        }
}
</script>