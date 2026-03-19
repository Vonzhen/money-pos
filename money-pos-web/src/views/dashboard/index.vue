<template>
    <PageWrapper>
        <div class="mb-4 flex justify-between items-center">
            <h2 class="text-xl font-black text-gray-800 flex items-center gap-2 tracking-widest">
                <svg-icon name="sys-manage" class="w-6 h-6 text-blue-600" />
                门店经营作战室
            </h2>
            <el-radio-group v-model="activeName" size="default" class="shadow-sm" @change="loadDynamicCharts">
                <el-radio-button label="today">今日</el-radio-button>
                <el-radio-button label="month">本月</el-radio-button>
                <el-radio-button label="year">本年</el-radio-button>
                <el-radio-button label="total">总计/库存</el-radio-button>
            </el-radio-group>
        </div>

        <div class="mb-4 space-y-3" v-if="activeName === 'today' && engineAlerts.length > 0">
            <div class="text-sm font-bold text-gray-500 mb-2 flex items-center">
                <el-icon class="mr-1"><Aim /></el-icon> 智能经营诊断 (基于近7日均值比对)
            </div>
            <el-alert
                v-for="(alert, idx) in engineAlerts"
                :key="idx"
                :title="alert.title + '：' + alert.desc"
                :type="alert.type"
                show-icon
                :closable="false"
                class="!font-bold !border"
                :class="{
                    '!border-red-200': alert.type === 'error',
                    '!border-orange-200': alert.type === 'warning',
                    '!border-blue-200': alert.type === 'info',
                    '!border-green-200': alert.type === 'success'
                }"
            />
        </div>

        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4" v-loading="chartLoading">
            <el-card v-for="(item, idx) in currentKpiData" :key="idx" shadow="hover" class="border-none shadow-sm rounded-xl bg-gradient-to-br from-gray-50 to-white relative overflow-hidden">
                <div class="text-gray-500 text-sm font-bold mb-2">{{ item.title }}</div>
                <div class="flex items-end justify-between">
                    <MoneyDisplay v-if="item.isMoney" :value="item.count" size="3xl" split custom-class="text-gray-800 font-black tracking-tighter" />
                    <span v-else class="font-black text-3xl text-gray-800 tracking-tighter">{{ item.count }}</span>

                    <div v-if="activeName !== 'total' && item.trend !== undefined && item.trend !== null"
                         class="flex items-center text-sm font-bold px-2 py-1 rounded"
                         :class="item.trend >= 0 ? 'text-red-500 bg-red-50' : 'text-green-600 bg-green-50'">
                        <el-icon><CaretTop v-if="item.trend >= 0"/><CaretBottom v-else/></el-icon>
                        {{ Math.abs(item.trend) }}%
                    </div>
                </div>
                <div class="text-xs text-gray-400 mt-2 font-mono">
                    {{ activeName === 'today' ? 'vs 过去7日均值' : (activeName === 'month' ? 'vs 上月同期' : 'vs 去年') }}
                </div>
            </el-card>

            <el-card v-if="activeName === 'total'" shadow="hover" class="border-none shadow-sm rounded-xl bg-gradient-to-br from-blue-50 to-white">
                <div class="text-blue-800 text-sm font-bold mb-2">📦 当前库存总货值 (成本)</div>
                <MoneyDisplay :value="inventoryValue" size="3xl" split custom-class="text-blue-600 font-black tracking-tighter" />
                <div class="text-xs text-blue-400 mt-2 font-mono">压在店里的真金白银</div>
            </el-card>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4" v-loading="chartLoading">
            <el-card shadow="hover" class="lg:col-span-2 border-none shadow-sm rounded-xl">
                <template #header>
                    <span class="font-black text-gray-700 flex items-center gap-2 tracking-wider">
                        📈 {{ trendTitle }}
                    </span>
                </template>
                <div ref="trendChartRef" class="w-full h-[320px]"></div>
            </el-card>

            <el-card shadow="hover" class="border-none shadow-sm rounded-xl">
                <template #header>
                    <span class="font-black text-gray-700 flex items-center gap-2 tracking-wider">🍩 品牌矩阵营收贡献比</span>
                </template>
                <div ref="brandPieChartRef" class="w-full h-[320px]"></div>
            </el-card>

            <el-card shadow="hover" class="lg:col-span-3 border-none shadow-sm rounded-xl mt-1">
                <template #header>
                    <span class="font-black text-gray-700 flex items-center gap-2 tracking-wider">👑 品牌高等级会员分布矩阵</span>
                </template>
                <div ref="memberBarChartRef" class="w-full h-[280px]"></div>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyDisplay from "@/components/common/MoneyDisplay.vue";
import { Aim, CaretTop, CaretBottom } from '@element-plus/icons-vue';
import { onMounted, onUnmounted, ref, nextTick, computed } from "vue";
import homeApi from "@/api/dashboard/home.js";
import dictApi from "@/api/system/dict.js";
import * as echarts from 'echarts';

// --- 状态控制 ---
const activeName = ref('today')
const homeCountMap = ref({})
const inventoryValue = ref(0)
const engineAlerts = ref([])
const chartLoading = ref(true)

const memberTypes = ref([])
const chartsData = ref({})

const trendChartRef = ref(null)
const brandPieChartRef = ref(null)
const memberBarChartRef = ref(null)
let charts = []

// 动态计算图表标题
const trendTitle = computed(() => {
    const map = {
        'today': '近7日营业额与利润趋势',
        'month': '本月营业额与利润趋势',
        'year': '本年营业额与利润趋势',
        'total': '累计营业额与利润趋势'
    };
    return map[activeName.value] || '营业额与利润趋势';
});

const currentKpiData = computed(() => {
    return homeCountMap.value[activeName.value] || []
})

// 🌟 核心新增：专门用于图表动态拉取与重绘的方法
const loadDynamicCharts = async () => {
    chartLoading.value = true;
    try {
        // 带着当选点选的时间范围去请求
        const chartRes = await homeApi.getChartsData({ timeRange: activeName.value });
        chartsData.value = chartRes.data || chartRes || {};
        // 数据到了后，执行重绘
        nextTick(() => { drawAllCharts(); });
    } catch (error) {
        console.error("动态拉取图表失败:", error);
    } finally {
        chartLoading.value = false;
    }
}

// 首次进入页面的初始化大盘数据
const initDashboardData = async () => {
    chartLoading.value = true;
    try {
        const dataRes = await homeApi.getHomeCount()
        const data = dataRes.data || dataRes
        inventoryValue.value = data.inventoryValue || 0
        engineAlerts.value = data.alerts || []

        const flatGet = (d) => {
            if (!d) return []
            const aov = d.orderCount > 0 ? (d.saleCount / d.orderCount) : 0;
            return [
                { title: '实收营业额', count: d.saleCount || 0, isMoney: true, trend: d.salesTrend ? Number(d.salesTrend) : undefined },
                { title: '净利润', count: d.profit || 0, isMoney: true, trend: d.profitTrend ? Number(d.profitTrend) : undefined },
                { title: '客单价 (ASP)', count: aov, isMoney: true, trend: d.aspTrend ? Number(d.aspTrend) : undefined },
                { title: '交易单量', count: d.orderCount || 0, isMoney: false, trend: d.ordersTrend ? Number(d.ordersTrend) : undefined }
            ]
        }

        homeCountMap.value = {
            'today': flatGet(data.today),
            'month': flatGet(data.month),
            'year': flatGet(data.year),
            'total': flatGet(data.total)
        }

        const dictRes = await dictApi.loadDict(["memberType"])
        memberTypes.value = (dictRes.memberType || []).filter(item => item.value !== 'MEMBER')

        // 初始拉取一次图表数据 (带上默认的 today)
        const chartRes = await homeApi.getChartsData({ timeRange: activeName.value })
        chartsData.value = chartRes.data || chartRes || {}

    } catch (error) {
        console.error("Dashboard 初始化异常:", error)
    } finally {
        chartLoading.value = false;
        nextTick(() => { drawAllCharts(); })
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
    // 🌟 优化：使用 echarts.getInstanceByDom 防止内存警告，提升渲染性能
    if (trendChartRef.value) {
        const trend = chartsData.value.trendData || []
        const dates = trend.map(item => item.date)
        const sales = trend.map(item => item.sales)
        const profits = trend.map(item => item.profit)

        let trendChart = echarts.getInstanceByDom(trendChartRef.value);
        if (!trendChart) {
            trendChart = echarts.init(trendChartRef.value);
            charts.push(trendChart);
        }

        trendChart.setOption({
            tooltip: { trigger: 'axis', appendToBody: true },
            legend: { data: ['销售额', '净利润'], bottom: 0 },
            grid: { left: '2%', right: '4%', bottom: '10%', top: '8%', containLabel: true },
            xAxis: { type: 'category', boundaryGap: false, data: dates.length ? dates : ['无数据'] },
            yAxis: { type: 'value' },
            series: [
                { name: '销售额', type: 'line', smooth: true, symbolSize: 6, itemStyle: { color: '#3b82f6' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{offset: 0, color: 'rgba(59, 130, 246, 0.3)'}, {offset: 1, color: 'rgba(59, 130, 246, 0.05)'}]) }, data: sales },
                { name: '净利润', type: 'line', smooth: true, symbolSize: 6, itemStyle: { color: '#10b981' }, data: profits }
            ]
        }, true); // 第二个参数 true 表示彻底重绘，防止旧数据残留
    }

    if (brandPieChartRef.value) {
        let pie = chartsData.value.pieData || []
        pie = pie.filter(item => item.name && item.name !== '1' && item.name !== '无品牌/未知' && !item.name.includes('套餐'))

        let pieChart = echarts.getInstanceByDom(brandPieChartRef.value);
        if (!pieChart) {
            pieChart = echarts.init(brandPieChartRef.value);
            charts.push(pieChart);
        }

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
        }, true);
    }

    if (memberBarChartRef.value) {
        let barData = chartsData.value.barData || []
        barData = barData.filter(item => item.brandName && item.brandName !== '1' && item.brandName !== '无品牌/未知' && !item.brandName.includes('套餐'))

        const brandNames = [...new Set(barData.map(item => item.brandName))]
        const allLevelCodes = [...new Set(barData.map(item => item.levelCode))]

        const validLevelCodes = allLevelCodes.filter(code => memberTypes.value.some(m => m.value === code))

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
                name: cnName, type: 'bar', stack: 'total', barWidth: '40%',
                label: { show: true, formatter: function(params) { return params.value > 0 ? params.value : ''; } },
                data: counts
            }
        })

        let barChart = echarts.getInstanceByDom(memberBarChartRef.value);
        if (!barChart) {
            barChart = echarts.init(memberBarChartRef.value);
            charts.push(barChart);
        }

        barChart.setOption({
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, appendToBody: true },
            legend: { data: legendData.length ? legendData : ['无数据'], top: 0 },
            grid: { left: '2%', right: '3%', bottom: '3%', top: '15%', containLabel: true },
            xAxis: { type: 'value', minInterval: 1, axisLabel: { formatter: '{value}' } },
            yAxis: { type: 'category', data: brandNames.length ? brandNames : ['暂无品牌'], axisTick: { show: false }, axisLine: { lineStyle: { color: '#ccc' } }, axisLabel: { color: '#666', fontWeight: 'bold' } },
            series: barSeries.length ? barSeries : [{ name: '无数据', type: 'bar', data: [0] }]
        }, true);
    }
}
</script>