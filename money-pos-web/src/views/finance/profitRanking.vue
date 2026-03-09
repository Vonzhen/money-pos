<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="text-yellow-500 mr-2"><Trophy /></el-icon> 门店利润暴击榜
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">统计近 30 天内，为门店创造最多真实毛利的 Top 50 明星商品</p>
                </div>
                <el-button type="primary" plain @click="fetchData">
                    <el-icon class="mr-1"><Refresh /></el-icon> 刷新榜单
                </el-button>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <el-card shadow="hover" class="rounded-lg" header="🏆 Top 20 吸金王 (绝对毛利额)">
                    <div ref="barChartRef" style="height: 700px; width: 100%;"></div>
                    <div v-if="rankingData.length === 0" class="text-center text-gray-400 mt-[-350px]">
                        暂无销售数据
                    </div>
                </el-card>

                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📋 详细利润账单 (Top 50)">
                    <el-table :data="rankingData" height="700" stripe style="width: 100%" v-loading="loading">
                        <el-table-column type="index" label="排名" width="80" align="center">
                            <template #default="scope">
                                <div class="font-bold flex items-center justify-center">
                                    <span v-if="scope.$index === 0" class="text-2xl text-yellow-500">🥇</span>
                                    <span v-else-if="scope.$index === 1" class="text-xl text-gray-400">🥈</span>
                                    <span v-else-if="scope.$index === 2" class="text-xl text-yellow-700">🥉</span>
                                    <span v-else class="text-gray-500">{{ scope.$index + 1 }}</span>
                                </div>
                            </template>
                        </el-table-column>

                        <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip>
                            <template #default="scope">
                                <span class="font-medium text-gray-800">{{ scope.row.goodsName }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column prop="totalQuantity" label="售出总数" width="100" align="center" sortable>
                            <template #default="scope">
                                <el-tag size="small" type="info">{{ scope.row.totalQuantity }} 件</el-tag>
                            </template>
                        </el-table-column>

                        <el-table-column prop="totalSales" label="创造营业额" width="120" align="right" sortable>
                            <template #default="scope">
                                ¥ {{ formatMoney(scope.row.totalSales) }}
                            </template>
                        </el-table-column>

                        <el-table-column prop="totalProfit" label="净赚毛利润" width="120" align="right" sortable>
                            <template #default="scope">
                                <span class="text-red-500 font-bold">¥ {{ formatMoney(scope.row.totalProfit) }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column label="单品毛利率" width="120" align="right" sortable :sort-method="sortByMargin">
                            <template #default="{row}">
                                <div v-if="row.totalSales > 0">
                                    <el-tag
                                        :type="(row.totalProfit / row.totalSales) >= 0.5 ? 'danger' : ((row.totalProfit / row.totalSales) >= 0.3 ? 'warning' : 'info')"
                                        effect="dark"
                                        size="small"
                                        class="font-mono tracking-widest"
                                    >
                                        {{ ((row.totalProfit / row.totalSales) * 100).toFixed(2) }}%
                                    </el-tag>
                                </div>
                                <span v-else class="text-gray-400">0.00%</span>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import financeApi from "@/api/finance/finance.js"
import { Refresh, Trophy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const rankingData = ref([])
const loading = ref(false)
const barChartRef = ref(null)
let barChart = null

const fetchData = async () => {
    loading.value = true
    try {
        const res = await financeApi.getProfitRanking()
        rankingData.value = res.data || res || [] // 容错处理
        nextTick(() => { initBarChart() })
    } catch (error) {
        ElMessage.error("获取利润榜单失败")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const sortByMargin = (a, b) => {
    const marginA = a.totalSales > 0 ? (a.totalProfit / a.totalSales) : 0;
    const marginB = b.totalSales > 0 ? (b.totalProfit / b.totalSales) : 0;
    return marginA - marginB;
}

const initBarChart = () => {
    if (!barChartRef.value || rankingData.value.length === 0) return
    if (!barChart) barChart = echarts.init(barChartRef.value)

    // 🌟 核心修复：切片改为 Top 20
    const top20 = rankingData.value.slice(0, 20).reverse()
    const names = top20.map(item => item.goodsName)
    const profits = top20.map(item => item.totalProfit)

    barChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        // 🌟 核心修复：把 right 改为 '12%'，给右侧的数字标签腾出足够的物理空间
        grid: { left: '3%', right: '12%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value', name: '毛利 (元)' },
        yAxis: {
            type: 'category',
            data: names,
            axisLabel: { interval: 0, width: 100, overflow: 'truncate' }
        },
        series: [{
            name: '创造毛利',
            type: 'bar',
            barWidth: '55%',
            itemStyle: {
                color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
                    { offset: 0, color: '#ff9a9e' },
                    { offset: 1, color: '#fecfef' }
                ]),
                borderRadius: [0, 4, 4, 0]
            },
            label: {
                show: true,
                position: 'right',
                formatter: '¥ {c}',
                color: '#666',
                fontWeight: 'bold'
            },
            data: profits
        }]
    })
}

const handleResize = () => {
    if (barChart) barChart.resize()
}

onMounted(() => {
    window.addEventListener('resize', handleResize)
    fetchData()
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (barChart) barChart.dispose()
})
</script>