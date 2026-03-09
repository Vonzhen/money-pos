<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="text-orange-500 mr-2"><TrendCharts /></el-icon> 5.4 营销活动核销与成本复盘
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">分析每一分让利带来的真实收益，将会员券与满减券对比核算</p>
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

            <el-card shadow="hover" class="mb-6 rounded-lg" header="📈 营销活动 ROI 杠杆率走势">
                <div ref="chartRef" style="height: 350px; width: 100%;"></div>
            </el-card>

            <el-card shadow="hover" class="rounded-lg" header="📋 活动战绩明细 (ROI 降序)">
                <el-table :data="tableData" stripe border v-loading="loading">
                    <el-table-column prop="ruleName" label="营销活动/券名称" min-width="180">
                        <template #default="{row}">
                            <div class="font-bold">{{ row.ruleName }}</div>
                            <div class="text-xs text-gray-400">{{ row.ruleType }}</div>
                        </template>
                    </el-table-column>
                    <el-table-column prop="usedCount" label="核销张数" width="100" align="center" />
                    <el-table-column prop="totalDiscountGived" label="成本 (总让利)" width="150" align="right">
                        <template #default="{row}">
                            <span class="text-red-500 font-bold">¥ {{ formatMoney(row.totalDiscountGived) }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="totalRevenueBrought" label="拉动营业额" width="150" align="right">
                        <template #default="{row}">
                            <span class="text-green-600 font-bold">¥ {{ formatMoney(row.totalRevenueBrought) }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="avgOrderValue" label="活动客单价" width="120" align="right">
                        <template #default="{row}">
                            <span class="text-gray-700">¥ {{ formatMoney(row.avgOrderValue) }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="roiMultiplier" label="ROI 杠杆" width="120" align="center" fixed="right">
                        <template #default="{row}">
                            <el-tag :type="row.roiMultiplier > 5 ? 'success' : 'danger'" effect="dark" class="font-black">
                                {{ row.roiMultiplier }} x
                            </el-tag>
                        </template>
                    </el-table-column>
                </el-table>
            </el-card>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const dateRange = ref([dayjs().subtract(29, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')])
const tableData = ref([])
const loading = ref(false)
const chartRef = ref(null)
let myChart = null

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value
        const res = await req({ url: '/oms/analysis/marketing-roi', method: 'GET', params: { startDate, endDate } })
        tableData.value = res?.data || []
        nextTick(initChart)
    } catch (e) { console.error(e) } finally { loading.value = false }
}

const formatMoney = (val) => Number(val || 0).toFixed(2)

const initChart = () => {
    if (!chartRef.value || tableData.value.length === 0) return
    if (!myChart) myChart = echarts.init(chartRef.value)

    const names = tableData.value.map(d => d.ruleName)
    const rois = tableData.value.map(d => d.roiMultiplier)
    const costs = tableData.value.map(d => d.totalDiscountGived)

    myChart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: names },
        yAxis: [{ type: 'value', name: 'ROI 倍数' }, { type: 'value', name: '成本金额', position: 'right' }],
        series: [
            { name: 'ROI 倍数', type: 'line', data: rois, smooth: true, lineStyle: { width: 4 } },
            { name: '投入成本', type: 'bar', data: costs, yAxisIndex: 1, barWidth: 20 }
        ]
    })
}

onMounted(fetchData)
</script>