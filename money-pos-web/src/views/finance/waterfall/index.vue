<template>
    <PageWrapper>
        <div class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 mb-4 flex justify-between items-center">
            <div class="flex items-center gap-4">
                <span class="font-bold text-gray-700 text-base flex items-center gap-2">
                    <el-icon class="text-blue-500 text-xl"><DataAnalysis /></el-icon>
                    财务瀑布流全口径日结
                </span>
                <el-date-picker
                    v-model="dateRange"
                    type="daterange"
                    range-separator="至"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
                    @change="fetchData"
                    class="!w-[320px]"
                />
            </div>
            <div>
                <el-button type="primary" @click="fetchData" :loading="loading">
                    <el-icon class="mr-1"><Search /></el-icon> 重新核算
                </el-button>
            </div>
        </div>

        <div class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 h-[calc(100vh-220px)] flex flex-col">
            <el-table
                :data="tableData"
                v-loading="loading"
                stripe
                border
                height="100%"
                class="w-full text-sm custom-finance-table"
                show-summary
                :summary-method="getSummaries"
            >
                <el-table-column prop="date" label="账单日期" width="120" fixed="left" align="center">
                    <template #default="{row}">
                        <span class="font-bold text-gray-800">{{ row.date }}</span>
                    </template>
                </el-table-column>

                <el-table-column prop="totalAmount" label="应收总额" min-width="120" align="right">
                    <template #default="{row}"><span class="font-bold">￥{{ (row.totalAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="-" width="40" align="center"><template #default><span class="text-gray-300 font-black">-</span></template></el-table-column>

                <el-table-column prop="couponAmount" label="会员券抵扣" min-width="120" align="right">
                    <template #default="{row}"><span class="font-bold text-orange-500">￥{{ (row.couponAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="-" width="40" align="center"><template #default><span class="text-gray-300 font-black">-</span></template></el-table-column>

                <el-table-column prop="voucherAmount" label="满减券抵扣" min-width="120" align="right">
                    <template #default="{row}"><span class="font-bold text-red-400">￥{{ (row.voucherAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="-" width="40" align="center"><template #default><span class="text-gray-300 font-black">-</span></template></el-table-column>

                <el-table-column prop="manualDiscountAmount" label="整单优惠" min-width="120" align="right">
                    <template #default="{row}"><span class="font-bold text-red-500">￥{{ (row.manualDiscountAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="=" width="40" align="center"><template #default><span class="text-gray-300 font-black">=</span></template></el-table-column>

                <el-table-column prop="payAmount" label="实付进账" min-width="120" align="right">
                    <template #default="{row}"><span class="font-black text-blue-600 tracking-wider">￥{{ (row.payAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="-" width="40" align="center"><template #default><span class="text-gray-300 font-black">-</span></template></el-table-column>

                <el-table-column prop="refundAmount" label="售后退款" min-width="110" align="right">
                    <template #default="{row}"><span class="font-bold text-gray-500">￥{{ (row.refundAmount || 0).toFixed(2) }}</span></template>
                </el-table-column>

                <el-table-column label="=" width="40" align="center"><template #default><span class="text-gray-300 font-black">=</span></template></el-table-column>

                <el-table-column prop="netIncome" label="当日净收" min-width="130" align="right" fixed="right">
                    <template #default="{row}">
                        <div class="bg-green-50 text-green-600 font-black px-2 py-1 rounded border border-green-200 tracking-tighter text-base">
                            ￥{{ (row.netIncome || 0).toFixed(2) }}
                        </div>
                    </template>
                </el-table-column>
            </el-table>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageWrapper from "@/components/PageWrapper.vue"
import financeApi from '@/api/finance/report.js'
import dayjs from 'dayjs'

const dateRange = ref([])
const tableData = ref([])
const loading = ref(false)

// 默认查询近 7 天
onMounted(() => {
    const end = dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss')
    const start = dayjs().subtract(6, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    dateRange.value = [start, end]
    fetchData()
})

const fetchData = async () => {
    loading.value = true
    try {
        const params = {}
        if (dateRange.value && dateRange.value.length === 2) {
            params.startTime = dateRange.value[0]
            params.endTime = dateRange.value[1]
        }
        const res = await financeApi.getDailyWaterfall(params)
        tableData.value = res.data || res || []
    } catch (error) {
        ElMessage.error('获取财务报表失败')
    } finally {
        loading.value = false
    }
}

// 底部合计行逻辑
const getSummaries = (param) => {
    const { columns, data } = param
    const sums = []
    columns.forEach((column, index) => {
        if (index === 0) { sums[index] = '区间合计'; return; }
        // 跳过加减号列
        if (column.label === '-' || column.label === '=') { sums[index] = ''; return; }

        const values = data.map((item) => Number(item[column.property]))
        if (!values.every((value) => Number.isNaN(value))) {
            const total = values.reduce((prev, curr) => {
                const value = Number(curr)
                if (!Number.isNaN(value)) { return prev + curr } else { return prev }
            }, 0)
            sums[index] = '￥' + total.toFixed(2)
        } else {
            sums[index] = ''
        }
    })
    return sums
}
</script>

<style scoped>
:deep(.custom-finance-table .el-table__footer-wrapper tbody td.el-table__cell) {
    background-color: #f8fafc !important;
    font-weight: 900 !important;
    color: #1e293b !important;
}
</style>