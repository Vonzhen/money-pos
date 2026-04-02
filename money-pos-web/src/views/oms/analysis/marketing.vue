<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="text-orange-500 mr-2"><TrendCharts /></el-icon> 经营大盘：商品结构与爆品排榜
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">从品类宏观结构到细分爆品，精准定位门店利润引流引擎</p>
                </div>
                <div class="flex gap-4">
                    <el-date-picker
                        v-model="dateRange"
                        type="daterange"
                        range-separator="至"
                        value-format="YYYY-MM-DD"
                        @change="fetchData"
                        class="!w-[300px]"
                    />
                    <el-button type="primary" @click="fetchData" :loading="loading">更新报表</el-button>
                </div>
            </div>

            <div class="flex flex-col gap-6 mb-6">

                <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    <div class="lg:col-span-5">
                        <el-card shadow="hover" class="rounded-lg h-full">
                            <template #header>
                                <div class="font-bold text-gray-800">📊 品类销售额占比结构</div>
                            </template>
                            <div ref="categoryChartRef" style="height: 420px; width: 100%;"></div>
                        </el-card>
                    </div>

                    <div class="lg:col-span-7">
                        <el-card shadow="hover" class="rounded-lg h-full">
                            <template #header>
                                <div class="flex justify-between items-center">
                                    <span class="font-bold text-gray-800">🔥 Top 50 门店畅销爆品榜单</span>
                                    <span class="text-xs text-blue-500 font-bold"><el-icon><Pointer /></el-icon> 勾选单品，底部生成动销曲线 (最多5个)</span>
                                </div>
                            </template>
                            <el-table
                                ref="goodsTableRef"
                                :data="topGoodsRanking"
                                height="420"
                                stripe
                                style="width: 100%"
                                v-loading="loading"
                                @selection-change="handleSelectionChange"
                            >
                                <el-table-column type="selection" width="45" :selectable="canSelectGoods"></el-table-column>

                                <el-table-column type="index" label="热度" width="60" align="center">
                                    <template #default="scope">
                                        <span v-if="scope.$index < 3" class="text-xl text-red-500 font-black">🔥 {{ scope.$index + 1 }}</span>
                                        <span v-else class="text-gray-500 font-bold">{{ scope.$index + 1 }}</span>
                                    </template>
                                </el-table-column>

                                <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip>
                                    <template #default="{row}"><span class="font-bold text-gray-800">{{ row.goodsName }}</span></template>
                                </el-table-column>
                                <el-table-column prop="salesQty" label="净售出数量" width="110" align="center" sortable>
                                    <template #default="{row}"><el-tag type="danger" effect="dark" class="font-bold">{{ row.salesQty }} 件</el-tag></template>
                                </el-table-column>
                                <el-table-column prop="salesAmount" label="拉动营业额" width="130" align="right" sortable>
                                    <template #default="{row}"><span class="font-bold text-blue-600">¥ {{ formatMoney(row.salesAmount) }}</span></template>
                                </el-table-column>
                            </el-table>
                        </el-card>
                    </div>
                </div>

                <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    <div class="lg:col-span-5">
                        <el-card shadow="hover" class="rounded-lg h-full">
                            <template #header>
                                <div class="font-bold text-gray-800">🏆 门店品类创收排行榜</div>
                            </template>
                            <el-table :data="sortedCategoryData" height="320" stripe style="width: 100%" v-loading="loading">
                                <el-table-column type="index" label="排名" width="55" align="center">
                                    <template #default="scope">
                                        <span v-if="scope.$index < 3" class="text-lg text-red-500 font-black">{{ scope.$index + 1 }}</span>
                                        <span v-else class="text-gray-500 font-bold">{{ scope.$index + 1 }}</span>
                                    </template>
                                </el-table-column>
                                <el-table-column prop="categoryName" label="品类名称" min-width="100" show-overflow-tooltip>
                                    <template #default="{row}"><span class="font-bold text-gray-800">{{ row.categoryName }}</span></template>
                                </el-table-column>
                                <el-table-column prop="salesQty" label="售出件数" width="90" align="center">
                                    <template #default="{row}"><span class="font-bold text-gray-600">{{ row.salesQty }} 件</span></template>
                                </el-table-column>
                                <el-table-column prop="salesAmount" label="拉动营业额" min-width="110" align="right">
                                    <template #default="{row}"><span class="font-bold text-blue-600">¥ {{ formatMoney(row.salesAmount) }}</span></template>
                                </el-table-column>
                            </el-table>
                        </el-card>
                    </div>

                    <div class="lg:col-span-7">
                        <el-card shadow="hover" class="rounded-lg h-full">
                            <template #header>
                                <div class="font-bold text-gray-800">📈 单品生命周期动销趋势对比</div>
                            </template>

                            <div v-if="selectedGoodsIds.length === 0" class="h-[320px] flex items-center justify-center text-gray-400 text-sm bg-gray-50 rounded border border-dashed">
                                👆 请在上方畅销榜单中勾选要对比的商品
                            </div>

                            <div v-else ref="goodsTrendChartRef" style="height: 320px; width: 100%;" v-loading="trendLoading"></div>
                        </el-card>
                    </div>
                </div>

            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount, computed } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import analysisApi from "@/api/oms/analysis.js"
import { TrendCharts, Pointer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([dayjs().subtract(29, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')])
const categoryData = ref([])
const topGoodsRanking = ref([])
const loading = ref(false)

const categoryChartRef = ref(null)
let categoryChart = null

const goodsTableRef = ref(null)
const selectedGoodsIds = ref([])
const trendLoading = ref(false)
const goodsTrendChartRef = ref(null)
let goodsTrendChart = null

const dateLabels = computed(() => {
    const dates = []
    let curr = dayjs(dateRange.value[0])
    const end = dayjs(dateRange.value[1])
    while(curr.isBefore(end) || curr.isSame(end, 'day')) {
        dates.push(curr.format('MM-DD'))
        curr = curr.add(1, 'day')
    }
    return dates
})

const sortedCategoryData = computed(() => {
    return [...categoryData.value].sort((a, b) => (b.salesAmount || 0) - (a.salesAmount || 0))
})

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value

        const [catRes, dashboardRes] = await Promise.all([
            analysisApi.getCategorySales(startDate, endDate),
            req({ url: '/oms/analysis/dashboard', method: 'GET', params: { startDate, endDate } })
        ])

        categoryData.value = catRes?.data || []

        topGoodsRanking.value = (dashboardRes?.data?.topGoodsRanking || []).map((item, idx) => ({
            ...item,
            goodsId: item.goodsId || idx + 1
        }))

        if (goodsTableRef.value) goodsTableRef.value.clearSelection()
        selectedGoodsIds.value = []

        nextTick(() => {
            initCategoryChart()
        })
    } catch (e) {
        console.error(e)
        ElMessage.error("报表数据获取失败")
    } finally {
        loading.value = false
    }
}

const canSelectGoods = (row) => {
    if (selectedGoodsIds.value.includes(row.goodsId)) return true;
    return selectedGoodsIds.value.length < 5;
}

const handleSelectionChange = (val) => {
    selectedGoodsIds.value = val.map(v => v.goodsId);

    if (selectedGoodsIds.value.length > 0) {
        fetchGoodsTrend();
    } else {
        if (goodsTrendChart) {
            goodsTrendChart.dispose();
            goodsTrendChart = null;
        }
    }
}

const fetchGoodsTrend = async () => {
    if(selectedGoodsIds.value.length === 0) return;
    trendLoading.value = true;
    try {
        const [startDate, endDate] = dateRange.value
        const res = await req({
            url: '/oms/analysis/top-goods-trend',
            method: 'GET',
            params: { startDate, endDate, goodsIds: selectedGoodsIds.value.join(',') }
        });

        const trendData = res.data || [];
        nextTick(() => { drawGoodsTrendChart(trendData); });
    } catch (e) {
        console.error("加载单品趋势失败", e);
        ElMessage.warning("加载动销曲线失败");
    } finally {
        trendLoading.value = false;
    }
}

const drawGoodsTrendChart = (trendData) => {
    if (!goodsTrendChartRef.value) return;

    if (goodsTrendChart && goodsTrendChart.getDom() !== goodsTrendChartRef.value) {
        goodsTrendChart.dispose();
        goodsTrendChart = null;
    }

    if (!goodsTrendChart) {
        goodsTrendChart = echarts.init(goodsTrendChartRef.value);
    }

    const seriesData = trendData.map(item => ({
        name: item.goodsName,
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: item.trendSalesQty || []
    }));

    const option = {
        tooltip: {
            trigger: 'axis',
            appendToBody: true,
            backgroundColor: 'rgba(255, 255, 255, 0.95)'
        },
        // 🌟 核心修复：强制设置为 plain 模式，允许自然换行，拒绝左右翻页箭头！
        legend: {
            type: 'plain',
            bottom: 0,
            width: '95%', // 限制宽度，促使其换行
            textStyle: { fontSize: 12 }
        },
        // 🌟 留出底部空间：从 15% 扩大到 25%，为多行换行的图例留出充裕的展示区
        grid: { left: '3%', right: '4%', top: '10%', bottom: '25%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: dateLabels.value },
        yAxis: { type: 'value', name: '售出件数', minInterval: 1 },
        series: seriesData
    };

    goodsTrendChart.setOption(option, true);
}


const formatMoney = (val) => Number(val || 0).toFixed(2)

const initCategoryChart = () => {
    if (!categoryChartRef.value) return
    if (!categoryChart) { categoryChart = echarts.init(categoryChartRef.value) }

    if (categoryData.value.length === 0) {
        categoryChart.clear()
        categoryChart.setOption({
            title: { text: '暂无分类数据', left: 'center', top: 'center', textStyle: { color: '#9ca3af' } }
        }, true)
        return
    }

    const pieData = categoryData.value.map(item => ({ name: item.categoryName, value: item.salesAmount, qty: item.salesQty }))

    categoryChart.setOption({
        tooltip: { trigger: 'item', appendToBody: true, formatter: params => `${params.name}<br/>销售额：¥${params.value} (${params.percent}%)<br/>件数：${params.data.qty}` },
        legend: { type: 'scroll', orient: 'vertical', right: '5%', top: 'middle' },
        series: [{
            type: 'pie', radius: ['45%', '70%'], center: ['35%', '50%'], label: { show: false }, labelLine: { show: false }, itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 }, data: pieData
        }]
    }, true)
}

const handleResize = () => {
    if (categoryChart) categoryChart.resize()
    if (goodsTrendChart) goodsTrendChart.resize()
}

onMounted(() => {
    fetchData()
    window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (categoryChart) { categoryChart.dispose(); categoryChart = null }
    if (goodsTrendChart) { goodsTrendChart.dispose(); goodsTrendChart = null }
})
</script>