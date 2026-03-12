<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <h2 class="text-2xl font-bold text-gray-800">📊 财务瀑布流全口径日结大屏</h2>
                <div class="flex items-center gap-4">
                    <el-date-picker
                        v-model="queryDate"
                        type="date"
                        placeholder="选择日结日期"
                        @change="fetchData"
                        value-format="YYYY-MM-DD"
                        :clearable="false"
                        class="!w-40"
                    />
                    <el-button type="primary" @click="fetchData" :loading="loading" class="font-bold tracking-widest">
                        <el-icon class="mr-1"><Refresh /></el-icon> 重新核算
                    </el-button>
                </div>
            </div>

            <div class="bg-white p-6 rounded-lg shadow-sm mb-6 border border-gray-200">
                <div class="text-sm font-black text-gray-600 mb-4 flex items-center">
                    <el-icon class="mr-2 text-blue-500"><List /></el-icon> 资金链路推演等式
                </div>
                <div class="flex flex-wrap items-center justify-between text-center gap-2">
                    <div class="flex-1 min-w-[120px]">
                        <div class="text-gray-400 text-xs font-bold mb-1">吊牌应收总计</div>
                        <div class="text-2xl font-mono font-bold text-gray-800">¥ {{ formatMoney(data.totalAmount) }}</div>
                    </div>
                    <div class="text-2xl text-gray-300 font-black">-</div>

                    <div class="flex-1 min-w-[120px]">
                        <div class="text-gray-400 text-xs font-bold mb-1">活动让利 (券/抹零)</div>
                        <div class="text-2xl font-mono font-bold text-red-500">¥ {{ formatMoney(data.totalDiscount) }}</div>
                    </div>
                    <div class="text-2xl text-gray-300 font-black">=</div>

                    <div class="flex-1 min-w-[120px]">
                        <div class="text-gray-400 text-xs font-bold mb-1">实付总计 (含余额/扫码/现金)</div>
                        <div class="text-2xl font-mono font-bold text-blue-600">¥ {{ formatMoney(data.payAmount) }}</div>
                    </div>
                    <div class="text-2xl text-gray-300 font-black">-</div>

                    <div class="flex-1 min-w-[120px]">
                        <div class="text-gray-400 text-xs font-bold mb-1">售后退款冲回</div>
                        <div class="text-2xl font-mono font-bold text-orange-500">¥ {{ formatMoney(data.refundAmount) }}</div>
                    </div>
                    <div class="text-2xl text-gray-300 font-black">=</div>

                    <div class="flex-1 min-w-[130px] bg-green-50 rounded-lg p-3 border border-green-200 shadow-inner">
                        <div class="text-green-700 text-xs font-black tracking-widest mb-1">最终实际净收</div>
                        <div class="text-3xl font-mono font-black text-green-600 tracking-tighter">¥ {{ formatMoney(data.netIncome) }}</div>
                    </div>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                <el-card shadow="hover" class="border-t-4 border-t-green-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-bold">当日纯利润 (毛利)</div>
                            <div class="text-3xl font-mono font-bold text-green-600">¥ {{ formatMoney(data.grossProfit) }}</div>
                        </div>
                        <div class="p-3 bg-green-50 rounded-full"><el-icon class="text-2xl text-green-500"><DataLine /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400 font-mono">净收金额 - 当日售出商品总进价成本</div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-blue-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-bold">全店真金白银流水入账</div>
                            <div class="text-3xl font-mono font-bold text-blue-600">¥ {{ formatMoney(data.externalIncome) }}</div>
                        </div>
                        <div class="p-3 bg-blue-50 rounded-full"><el-icon class="text-2xl text-blue-500"><Wallet /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400 font-mono">当日顾客扫码/现金消费总额 + 储值卡充值总额</div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-purple-500 rounded-lg">
                    <div class="flex items-center justify-between">
                        <div>
                            <div class="text-sm text-gray-500 mb-1 font-bold">全店隐形总负债 (沉淀资金)</div>
                            <div class="text-3xl font-mono font-bold text-purple-600">¥ {{ formatMoney(data.totalDebt) }}</div>
                        </div>
                        <div class="p-3 bg-purple-50 rounded-full"><el-icon class="text-2xl text-purple-500"><Warning /></el-icon></div>
                    </div>
                    <div class="mt-4 text-xs text-gray-400 font-mono">全体会员储值卡内尚未消费的本金余额</div>
                </el-card>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📈 近 7 天真实资金流水趋势解剖">
                    <div ref="lineChartRef" style="height: 350px; width: 100%;"></div>
                </el-card>

                <el-card shadow="hover" class="rounded-lg" header="🧮 财务漏斗 (全资金渠道拆解)">
                    <div ref="pieChartRef" style="height: 350px; width: 100%;"></div>
                    <div v-if="!data.payBreakdown || data.payBreakdown.length === 0" class="text-center text-gray-400 text-sm mt-[-180px]">
                        该日暂无资金活动记录
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
import dictApi from "@/api/system/dict.js"
import { Refresh, Wallet, DataLine, List, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const queryDate = ref(dayjs().format('YYYY-MM-DD'))
const loading = ref(false)
const payTagDict = ref([])

const data = ref({
    totalAmount: 0, totalDiscount: 0, payAmount: 0, refundAmount: 0, netIncome: 0, grossProfit: 0, externalIncome: 0, totalDebt: 0,
    payBreakdown: [], trendDates: [],
    trendScan: [], trendCash: [], trendRecharge: [], trendTotal: [],
    dynamicTrendMap: {}
})

const lineChartRef = ref(null)
const pieChartRef = ref(null)
let lineChart = null
let pieChart = null

// 🌟 全局拦截器：去掉所有名称里的“流水”二字，让界面清爽
const getTranslatedName = (rawName) => {
    let finalName = rawName;
    if (rawName && rawName.startsWith('TAG:')) {
        const code = rawName.substring(4);
        if (code === 'UNKNOWN') {
            finalName = '未分类扫码';
        } else {
            const dictItem = payTagDict.value.find(item => item.value === code);
            finalName = dictItem ? dictItem.desc : code;
        }
    }
    // 把后端传过来的“现金收银流水”、“聚合扫码流水”里的“流水”暴力切掉
    return finalName ? finalName.replace(/流水/g, '') : '';
}

const fetchData = async () => {
    loading.value = true
    try {
        const res = await req({
            url: '/finance/dashboard',
            method: 'GET',
            params: { date: queryDate.value }
        })
        data.value = res?.data || res || {}
        nextTick(() => { initLineChart(); initPieChart(); })
    } catch (error) {
        ElMessage.error("财务日结数据拉取失败，请检查网络")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const initLineChart = () => {
    if (!lineChartRef.value) return
    if (!lineChart) lineChart = echarts.init(lineChartRef.value)

    // 🌟 把基础类目的“流水”也精简掉
    const legendData = ['全口径大盘总计', '扫码总计', '现金收银', '会员充值'];
    const seriesData = [
        {
            name: '全口径大盘总计', type: 'line', smooth: true,
            itemStyle: { color: '#67C23A' },
            lineStyle: { width: 3, type: 'dashed' },
            data: data.value.trendTotal || []
        },
        {
            name: '扫码总计', type: 'line', smooth: true,
            areaStyle: { color: 'rgba(64, 158, 255, 0.2)' },
            itemStyle: { color: '#409EFF' },
            data: data.value.trendScan || []
        },
        {
            name: '现金收银', type: 'line', smooth: true,
            areaStyle: { color: 'rgba(156, 39, 176, 0.2)' },
            itemStyle: { color: '#9C27B0' },
            data: data.value.trendCash || []
        },
        {
            name: '会员充值', type: 'line', smooth: true,
            areaStyle: { color: 'rgba(230, 162, 60, 0.2)' },
            itemStyle: { color: '#E6A23C' },
            data: data.value.trendRecharge || []
        }
    ];

    if (data.value.dynamicTrendMap) {
        Object.keys(data.value.dynamicTrendMap).forEach(key => {
            const translatedName = getTranslatedName('TAG:' + key);
            legendData.push(translatedName);
            seriesData.push({
                name: translatedName,
                type: 'line',
                smooth: true,
                lineStyle: { type: 'dotted', width: 2 },
                symbolSize: 6,
                data: data.value.dynamicTrendMap[key]
            });
        });
    }

    lineChart.setOption({
        tooltip: { trigger: 'axis', appendToBody: true },
        // 🌟 取消了 type: 'scroll'，允许自然换行
        legend: { data: legendData, bottom: '0' },
        // 🌟 把 bottom 从 15% 拉大到了 20%，给两排标签留足空间防止遮挡 x 轴
        grid: { left: '3%', right: '4%', bottom: '20%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: data.value.trendDates || [] },
        yAxis: { type: 'value', name: '金额 (元)' },
        series: seriesData
    }, true)
}

const initPieChart = () => {
    if (!pieChartRef.value) return
    if (!pieChart) pieChart = echarts.init(pieChartRef.value)

    const translatedPieData = (data.value.payBreakdown || []).map(item => ({
        name: getTranslatedName(item.name),
        value: item.value
    }))

    pieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: ¥ {c} ({d}%)', appendToBody: true },
        // 🌟 同样取消了翻页模式，自然铺开两排
        legend: { orient: 'horizontal', bottom: '0' },
        color: ['#409EFF', '#9C27B0', '#67C23A', '#E6A23C', '#F56C6C', '#13CE66', '#FF9900', '#8e44ad', '#e74c3c'],
        series: [{
            name: '资金占比', type: 'pie',
            // 🌟 稍微上移了饼图本体 (center属性)，给底部多行标签腾出位置
            radius: ['40%', '65%'], center: ['50%', '42%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
            label: { show: false, position: 'center' },
            emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
            labelLine: { show: false },
            data: translatedPieData
        }]
    }, true)
}

const handleResize = () => {
    if (lineChart) lineChart.resize()
    if (pieChart) pieChart.resize()
}

onMounted(async () => {
    try {
        const dict = await dictApi.loadDict(["paySubTag"])
        if (dict.paySubTag) payTagDict.value = dict.paySubTag
    } catch (e) {}

    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (lineChart) lineChart.dispose()
    if (pieChart) pieChart.dispose()
})
</script>