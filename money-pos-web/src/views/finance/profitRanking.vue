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
                <el-card shadow="hover" class="rounded-lg" header="🏆 Top 10 吸金王 (毛利分布)">
                    <div ref="barChartRef" style="height: 500px; width: 100%;"></div>
                    <div v-if="rankingData.length === 0" class="text-center text-gray-400 mt-[-250px]">
                        暂无销售数据
                    </div>
                </el-card>

                <el-card shadow="hover" class="lg:col-span-2 rounded-lg" header="📋 详细利润账单 (Top 50)">
                    <el-table :data="rankingData" height="500" stripe style="width: 100%" v-loading="loading">
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
                    </el-table>
                </el-card>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
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
        rankingData.value = res.data || []

        nextTick(() => {
            initBarChart()
        })
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

const initBarChart = () => {
    if (!barChartRef.value || rankingData.value.length === 0) return
    if (!barChart) barChart = echarts.init(barChartRef.value)

    // 取前 10 名画图，并且要把数据倒过来（因为 ECharts 横向柱状图是从下往上画的）
    const top10 = rankingData.value.slice(0, 10).reverse()
    const names = top10.map(item => item.goodsName)
    const profits = top10.map(item => item.totalProfit)

    barChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value', name: '毛利 (元)' },
        yAxis: {
            type: 'category',
            data: names,
            axisLabel: {
                interval: 0,
                width: 100, // 限制宽度
                overflow: 'truncate' // 超出显示省略号
            }
        },
        series: [{
            name: '创造毛利',
            type: 'bar',
            barWidth: '60%',
            itemStyle: {
                color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
                    { offset: 0, color: '#ff9a9e' },
                    { offset: 1, color: '#fecfef' }
                ]),
                borderRadius: [0, 4, 4, 0]
            },
            label: { show: true, position: 'right', formatter: '¥ {c}' },
            data: profits
        }]
    })
}

window.addEventListener('resize', () => {
    if (barChart) barChart.resize()
})

onMounted(() => {
    fetchData()
})
</script>