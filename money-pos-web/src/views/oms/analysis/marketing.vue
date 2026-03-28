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

            <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">

                <div class="flex flex-col gap-6 lg:col-span-5">

                    <el-card shadow="hover" class="rounded-lg" header="📊 品类销售额占比结构">
                        <div ref="categoryChartRef" style="height: 380px; width: 100%;"></div>
                    </el-card>

                    <el-card shadow="hover" class="rounded-lg" header="🏆 门店品类创收排行榜">
                        <el-table :data="sortedCategoryData" height="420" stripe style="width: 100%" v-loading="loading">
                            <el-table-column type="index" label="排名" width="55" align="center">
                                <template #default="scope">
                                    <span v-if="scope.$index < 3" class="text-lg text-red-500 font-black">{{ scope.$index + 1 }}</span>
                                    <span v-else class="text-gray-500 font-bold">{{ scope.$index + 1 }}</span>
                                </template>
                            </el-table-column>

                            <el-table-column prop="categoryName" label="品类名称" min-width="100" show-overflow-tooltip>
                                <template #default="{row}">
                                    <span class="font-bold text-gray-800">{{ row.categoryName }}</span>
                                </template>
                            </el-table-column>

                            <el-table-column prop="salesQty" label="售出件数" width="90" align="center">
                                <template #default="{row}">
                                    <span class="font-bold text-gray-600">{{ row.salesQty }} 件</span>
                                </template>
                            </el-table-column>

                            <el-table-column prop="salesAmount" label="拉动营业额" min-width="110" align="right">
                                <template #default="{row}">
                                    <span class="font-bold text-blue-600">¥ {{ formatMoney(row.salesAmount) }}</span>
                                </template>
                            </el-table-column>
                        </el-table>
                    </el-card>
                </div>

                <div class="lg:col-span-7">
                    <el-card shadow="hover" class="rounded-lg h-full" header="🔥 Top 50 门店畅销爆品榜单 (按动销件数)">
                        <el-table :data="topGoodsRanking" height="850" stripe style="width: 100%" v-loading="loading">
                            <el-table-column type="index" label="热度" width="70" align="center">
                                <template #default="scope">
                                    <span v-if="scope.$index < 3" class="text-xl text-red-500 font-black">🔥 {{ scope.$index + 1 }}</span>
                                    <span v-else class="text-gray-500 font-bold">{{ scope.$index + 1 }}</span>
                                </template>
                            </el-table-column>

                            <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip>
                                <template #default="{row}">
                                    <span class="font-bold text-gray-800">{{ row.goodsName }}</span>
                                </template>
                            </el-table-column>

                            <el-table-column prop="salesQty" label="净售出数量" width="110" align="center" sortable>
                                <template #default="{row}">
                                    <el-tag type="danger" effect="dark" class="font-bold">{{ row.salesQty }} 件</el-tag>
                                </template>
                            </el-table-column>

                            <el-table-column prop="salesAmount" label="拉动营业额" width="130" align="right" sortable>
                                <template #default="{row}">
                                    <span class="font-bold text-blue-600">¥ {{ formatMoney(row.salesAmount) }}</span>
                                </template>
                            </el-table-column>
                        </el-table>
                    </el-card>
                </div>

            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import analysisApi from "@/api/oms/analysis.js"
import { TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([dayjs().subtract(29, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')])
const categoryData = ref([])
const topGoodsRanking = ref([])
const loading = ref(false)

const categoryChartRef = ref(null)

// 排序
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
        topGoodsRanking.value = dashboardRes?.data?.topGoodsRanking || []

        nextTick(() => {
            initCategoryChart()
        })
    } catch (e) {
        console.error(e)
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => Number(val || 0).toFixed(2)

const initCategoryChart = () => {
    if (!categoryChartRef.value) return
    let chart = echarts.getInstanceByDom(categoryChartRef.value)
    if (!chart) chart = echarts.init(categoryChartRef.value)

    if (categoryData.value.length === 0) {
        chart.clear()
        chart.setOption({
            title: { text: '暂无分类数据', left: 'center', top: 'center', textStyle: { color: '#9ca3af' } }
        }, true)
        return
    }

    const pieData = categoryData.value.map(item => ({
        name: item.categoryName,
        value: item.salesAmount,
        qty: item.salesQty
    }))

    chart.setOption({
        tooltip: {
            trigger: 'item',
            appendToBody: true,
            formatter: params =>
                `${params.name}<br/>销售额：¥${params.value} (${params.percent}%)<br/>件数：${params.data.qty}`
        },
        legend: {
            type: 'scroll',
            orient: 'vertical',
            right: '5%',
            top: 'middle'
        },
        series: [{
            type: 'pie',
            radius: ['45%', '70%'],
            center: ['35%', '50%'],
            label: { show: false },     // 🌟 隐藏文字
            labelLine: { show: false }, // 🌟 彻底干掉引出线
            itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 }, // 顺手加了个小圆角边框让饼图更好看
            data: pieData
        }]
    }, true)
}

const handleResize = () => {
    echarts.getInstanceByDom(categoryChartRef.value)?.resize()
}

onMounted(() => {
    fetchData()
    window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    echarts.getInstanceByDom(categoryChartRef.value)?.dispose()
})
</script>