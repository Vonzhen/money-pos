<template>
    <PageWrapper>
        <div class="p-6 bg-gray-50 min-h-full flex flex-col">
            <div class="flex items-center justify-between mb-6 shrink-0">
                <div>
                    <h2 class="text-2xl font-bold text-gray-800 flex items-center">
                        <el-icon class="mr-2 text-blue-600"><DocumentChecked /></el-icon> 经营业绩汇总报表
                    </h2>
                    <p class="text-sm text-gray-500 mt-1">支持按日、周、月维度进行业绩清算与核对，可用于导出述职</p>
                </div>
            </div>

            <div class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 mb-4 flex justify-between items-center shrink-0">
                <div class="flex items-center gap-6">
                    <el-radio-group v-model="dimension" size="large" @change="fetchData">
                        <el-radio-button value="DAILY">日结报表</el-radio-button>
                        <el-radio-button value="WEEKLY">周结报表</el-radio-button>
                        <el-radio-button value="MONTHLY">月结报表</el-radio-button>
                    </el-radio-group>

                    <el-date-picker
                        v-model="dateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="回溯开始日期"
                        end-placeholder="回溯结束日期"
                        value-format="YYYY-MM-DD"
                        :shortcuts="shortcuts"
                        @change="fetchData"
                        class="!w-[300px]"
                    />
                </div>

                <el-button type="success" plain disabled>
                    <el-icon class="mr-1"><Download /></el-icon> 导出 Excel (待开通)
                </el-button>
            </div>

            <div class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 flex-1 flex flex-col h-[500px]">
                <el-table
                    :data="tableData"
                    v-loading="loading"
                    stripe
                    border
                    height="100%"
                    class="w-full text-base"
                    show-summary
                    :summary-method="getSummaries"
                >
                    <el-table-column prop="period" label="统计周期" width="180" align="center" fixed="left">
                        <template #default="{row}">
                            <div class="font-bold text-gray-800 flex items-center justify-center gap-2">
                                <el-icon v-if="dimension === 'DAILY'" class="text-blue-400"><Calendar /></el-icon>
                                <el-icon v-else-if="dimension === 'WEEKLY'" class="text-orange-400"><Tickets /></el-icon>
                                <el-icon v-else class="text-purple-400"><FolderOpened /></el-icon>
                                {{ row.period }}
                            </div>
                        </template>
                    </el-table-column>

                    <el-table-column prop="orderCount" label="结单笔数" align="center" sortable>
                        <template #default="{row}">
                            <span class="font-mono font-bold">{{ row.orderCount }} <span class="text-xs text-gray-400 font-normal">单</span></span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="goodsCount" label="出库件数" align="center" sortable>
                        <template #default="{row}">
                            <span class="font-mono font-bold">{{ row.goodsCount }} <span class="text-xs text-gray-400 font-normal">件</span></span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="salesAmount" label="拉动营业额 (实收)" align="right" sortable min-width="150">
                        <template #default="{row}">
                            <span class="font-mono font-black text-blue-600 text-lg">¥ {{ formatMoney(row.salesAmount) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="avgOrderValue" label="平均客单价 (ASP)" align="right" sortable min-width="120">
                        <template #default="{row}">
                            <span class="font-mono font-bold text-green-600">¥ {{ formatMoney(row.avgOrderValue) }}</span>
                        </template>
                    </el-table-column>
                </el-table>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { req } from "@/api/index.js"
import { DocumentChecked, Calendar, Tickets, FolderOpened, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

const dimension = ref('DAILY')
const dateRange = ref([
    dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
])
const loading = ref(false)
const tableData = ref([])

const shortcuts = [
    { text: '最近30天', value: () => [dayjs().subtract(29, 'day').toDate(), dayjs().toDate()] },
    { text: '最近90天', value: () => [dayjs().subtract(89, 'day').toDate(), dayjs().toDate()] },
    { text: '今年以来', value: () => [dayjs().startOf('year').toDate(), dayjs().toDate()] }
]

const fetchData = async () => {
    loading.value = true
    try {
        const [startDate, endDate] = dateRange.value || ['', '']
        const res = await req({
            url: '/oms/analysis/report',
            method: 'GET',
            params: { startDate, endDate, dimension: dimension.value }
        })
        tableData.value = res?.data || res || []
    } catch (error) {
        ElMessage.error("获取报表数据失败")
    } finally {
        loading.value = false
    }
}

const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toFixed(2)
}

// Element Plus 底部合计行引擎
const getSummaries = (param) => {
    const { columns, data } = param;
    const sums = [];
    columns.forEach((column, index) => {
        if (index === 0) {
            sums[index] = '总计汇算';
            return;
        }

        const values = data.map(item => Number(item[column.property]));
        if (!values.every(value => isNaN(value))) {
            const total = values.reduce((prev, curr) => {
                const value = Number(curr);
                if (!isNaN(value)) return prev + curr;
                else return prev;
            }, 0);

            if (column.property === 'salesAmount' || column.property === 'avgOrderValue') {
                sums[index] = '¥ ' + total.toFixed(2);
            } else {
                sums[index] = total;
            }
        } else {
            sums[index] = '';
        }
    });

    // 核心修正：平均客单价的合计不能是简单相加，应该是 总销售额 / 总单数
    const totalOrder = sums[1];
    const totalSalesStr = sums[3]?.toString().replace('¥ ', '');
    const totalSales = Number(totalSalesStr);

    if (totalOrder > 0 && !isNaN(totalSales)) {
        sums[4] = '¥ ' + (totalSales / totalOrder).toFixed(2);
    } else {
        sums[4] = '¥ 0.00';
    }

    return sums;
}

onMounted(() => {
    fetchData()
})
</script>

<style scoped>
/* 强行穿透修改 Element Plus 底部合计行的样式，让它更像财务报表 */
:deep(.el-table__footer-wrapper tbody td.el-table__cell) {
    background-color: #f0f9eb;
    color: #333;
    font-weight: 900;
    font-size: 1.1em;
}
</style>