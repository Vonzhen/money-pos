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
import dictApi from "@/api/system/dict.js" // 🌟 引入字典 API
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)
const payTagDict = ref([]) // 🌟 存储子标签字典

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

// 🌟 翻译拦截器 (与大屏保持一致)
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
    return finalName ? finalName.replace(/流水/g, '') : '';
}

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

// 🌟 扩展调色板，以支持拆解后的多种扫码方式
const colorPalette = [
    '#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#9C27B0',
    '#13CE66', '#FF9900', '#8e44ad', '#e74c3c', '#909399'
]

const initStackChart = () => {
    if (!stackChartRef.value) return
    if (!stackChart) stackChart = echarts.init(stackChartRef.value)

    // 注意：由于后端 getChannelMixAnalysis 里的趋势数据（scanList）目前还是总计，
    // 所以这里的柱状图我们暂时保持总计展示，避免前端过于复杂。饼图则已经拆细。
    stackChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, appendToBody: true },
        // 设置 bottom 预留换行空间
        legend: { data: ['扫码总计(真金)', '现金收银(真金)', '余额消耗(预收)', '单品会员券(让利)', '整单满减券(让利)'], bottom: '0' },
        grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
        xAxis: { type: 'category', data: data.value.trendDates || [] },
        yAxis: { type: 'value', name: '金额成分' },
        series: [
            { name: '扫码总计(真金)', type: 'bar', stack: 'total', itemStyle: { color: '#409EFF' }, data: data.value.scanList || [] },
            { name: '现金收银(真金)', type: 'bar', stack: 'total', itemStyle: { color: '#9C27B0' }, data: data.value.cashList || [] },
            { name: '余额消耗(预收)', type: 'bar', stack: 'total', itemStyle: { color: '#909399' }, data: data.value.balanceList || [] },
            { name: '单品会员券(让利)', type: 'bar', stack: 'total', itemStyle: { color: '#F56C6C' }, data: data.value.couponList || [] },
            { name: '整单满减券(让利)', type: 'bar', stack: 'total', itemStyle: { color: '#E6A23C', borderRadius: [4, 4, 0, 0] }, data: data.value.voucherList || [] }
        ]
    }, true)
}

const initPieChart = () => {
    if (!pieChartRef.value) return
    if (!pieChart) pieChart = echarts.init(pieChartRef.value)

    // 🌟 翻译后端传来的饼图数据，让它显示成优美的中文
    const translatedPieData = (data.value.pieData || []).map(item => ({
        name: getTranslatedName(item.name),
        value: item.value
    }))

    pieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: ¥ {c} ({d}%)', appendToBody: true },
        // 允许标签自然换行，避免被隐藏
        legend: { orient: 'horizontal', bottom: '0' },
        color: colorPalette,
        series: [{
            name: '营业成分切片', type: 'pie',
            // 🌟 饼图稍微上移一点，给下方的双排文字留出空间
            radius: ['35%', '65%'], center: ['50%', '42%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, formatter: '{b}\n{d}%' },
            data: translatedPieData
        }]
    }, true)
}

const handleResize = () => {
    if (stackChart) stackChart.resize()
    if (pieChart) pieChart.resize()
}

onMounted(async () => {
    // 🌟 先拉取字典，再拉取业务数据
    try {
        const dict = await dictApi.loadDict(["paySubTag"])
        if (dict.paySubTag) payTagDict.value = dict.paySubTag
    } catch (e) {}

    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (stackChart) stackChart.dispose()
    if (pieChart) pieChart.dispose()
})
</script>