<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full">
            <div class="flex items-center justify-between mb-6">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="text-red-500 mr-2"><DataAnalysis /></el-icon> 满减营销大放血复盘
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">深度剖析店内的每一次满减活动，计算真实的投入产出比 (ROI杠杆)</p>
                </div>
                <el-button type="primary" plain @click="fetchData" :loading="loading">
                    <el-icon class="mr-1"><Refresh /></el-icon> 重新评估
                </el-button>
            </div>

            <el-card shadow="hover" class="mb-6 rounded-lg" header="📊 活动杠杆率对比图 (金额 VS 倍数)">
                <div ref="chartRef" style="height: 400px; width: 100%;"></div>
                <div v-if="reviewData.length === 0" class="text-center text-gray-400 mt-[-200px]">
                    暂无核销过满减券的订单数据
                </div>
            </el-card>

            <el-card shadow="hover" class="rounded-lg" header="📋 活动真实战绩榜 (按 ROI 倍数降序)">
                <el-table :data="reviewData" style="width: 100%" stripe v-loading="loading">
                    <el-table-column type="index" label="排名" width="70" align="center" />

                    <el-table-column prop="ruleName" label="营销活动名称" min-width="180">
                        <template #default="scope">
                            <span class="font-bold text-gray-800">{{ scope.row.ruleName }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="usedCount" label="核销(张)" width="100" align="center">
                        <template #default="scope">
                            <el-tag size="small" type="warning" effect="dark">{{ scope.row.usedCount }}</el-tag>
                        </template>
                    </el-table-column>

                    <el-table-column prop="totalDiscountGived" label="大放血 (总让利)" width="150" align="right">
                        <template #default="scope">
                            <span class="text-red-500 font-bold">- ¥ {{ formatMoney(scope.row.totalDiscountGived) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="totalRevenueBrought" label="撬动营业额" width="150" align="right">
                        <template #default="scope">
                            <span class="text-green-600 font-bold">+ ¥ {{ formatMoney(scope.row.totalRevenueBrought) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="roiMultiplier" label="ROI 杠杆倍数" width="150" align="center">
                        <template #default="scope">
                            <el-tag
                                :type="scope.row.roiMultiplier >= 5 ? 'success' : (scope.row.roiMultiplier <= 2 ? 'danger' : 'warning')"
                                effect="light"
                                class="!font-bold text-sm"
                            >
                                {{ scope.row.roiMultiplier }} x
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
import financeApi from "@/api/finance/finance.js"
import { Refresh, DataAnalysis } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const reviewData = ref([])
const loading = ref(false)
const chartRef = ref(null)
let mixChart = null

const fetchData = async () => {
    loading.value = true
    try {
        const res = await financeApi.getCampaignReview()
        reviewData.value = res.data || []

        nextTick(() => {
            initMixChart()
        })
    } catch (error) {
        ElMessage.error("获取活动复盘数据失败")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

const initMixChart = () => {
    if (!chartRef.value || reviewData.value.length === 0) return
    if (!mixChart) mixChart = echarts.init(chartRef.value)

    const names = reviewData.value.map(item => item.ruleName)
    const revenues = reviewData.value.map(item => item.totalRevenueBrought)
    const discounts = reviewData.value.map(item => item.totalDiscountGived)
    const rois = reviewData.value.map(item => item.roiMultiplier)

    mixChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
        legend: { data: ['撬动营业额 (元)', '让利金额 (元)', 'ROI 杠杆倍数'] },
        grid: { left: '3%', right: '4%', bottom: '5%', containLabel: true },
        // 核心技术：左边是金额轴，右边是倍数轴
        yAxis: [
            { type: 'value', name: '金额 (元)', position: 'left' },
            { type: 'value', name: '倍数 (x)', position: 'right', min: 0 }
        ],
        xAxis: { type: 'category', data: names, axisLabel: { interval: 0, rotate: 15 } },
        series: [
            {
                name: '撬动营业额 (元)',
                type: 'bar',
                barWidth: '20%',
                itemStyle: { color: '#67C23A', borderRadius: [4, 4, 0, 0] },
                data: revenues
            },
            {
                name: '让利金额 (元)',
                type: 'bar',
                barWidth: '20%',
                itemStyle: { color: '#F56C6C', borderRadius: [4, 4, 0, 0] },
                data: discounts
            },
            {
                name: 'ROI 杠杆倍数',
                type: 'line',
                yAxisIndex: 1, // 绑定到右边的倍数轴
                itemStyle: { color: '#E6A23C' },
                lineStyle: { width: 4 },
                symbolSize: 10,
                label: { show: true, position: 'top', formatter: '{c} x', fontSize: 14, fontWeight: 'bold' },
                data: rois
            }
        ]
    })
}

window.addEventListener('resize', () => {
    if (mixChart) mixChart.resize()
})

onMounted(() => {
    fetchData()
})
</script>